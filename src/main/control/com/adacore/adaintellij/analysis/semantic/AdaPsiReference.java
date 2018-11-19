package com.adacore.adaintellij.analysis.semantic;

import java.net.URL;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.eclipse.lsp4j.Location;

import com.adacore.adaintellij.lsp.AdaLSPDriver;

import static com.adacore.adaintellij.Utils.*;
import static com.adacore.adaintellij.lsp.LSPUtils.positionToOffset;
import static com.adacore.adaintellij.lsp.LSPUtils.offsetToPosition;

/**
 * Ada AST node representing an element that can reference other elements.
 * Typically such an element is an Ada identifier. By nature, an element
 * of this class always has a name and a corresponding element identifying
 * it, that element being itself, which is why this class implements
 * `PsiNameIdentifierOwner` (which in turn extends `PsiNamedElement`).
 *
 * For detailed information about the structure of ASTs built by the
 * Ada-IntelliJ Ada parser:
 * @see com.adacore.adaintellij.analysis.semantic.AdaParser
 */
public final class AdaPsiReference extends AdaPsiElement implements PsiReference, PsiNameIdentifierOwner {
	
	/**
	 * The underlying tree node.
	 */
	private ASTNode node;
	
	/**
	 * Constructs a new AdaPsiReference given a tree node.
	 *
	 * @param node The tree node to back the constructed
	 *             PSI reference.
	 */
	AdaPsiReference(@NotNull ASTNode node) {
		super(node);
		this.node = node;
	}
	
	/**
	 * @see com.intellij.psi.PsiNamedElement#getName()
	 */
	@NotNull
	@Override
	public String getName() { return node.getText(); }
	
	/**
	 * @see com.intellij.psi.PsiNamedElement#setName(String)
	 */
	@Override
	public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
		return this;
	}
	
	/**
	 * @see com.intellij.psi.PsiNameIdentifierOwner#getNameIdentifier()
	 */
	@NotNull
	@Override
	public PsiElement getNameIdentifier() { return this; }
	
	/**
	 * @see com.intellij.psi.PsiElement#getReferences()
	 */
	@Override
	@NotNull
	public PsiReference[] getReferences() { return new PsiReference[] { this }; }
	
	/**
	 * @see com.intellij.psi.PsiReference#getElement()
	 */
	@NotNull
	@Override
	public PsiElement getElement() { return this; }
	
	/**
	 * @see com.intellij.psi.PsiReference#getRangeInElement()
	 */
	@NotNull
	@Override
	public TextRange getRangeInElement() { return new TextRange(0, node.getTextLength()); }
	
	/**
	 * @see com.intellij.psi.PsiReference#resolve()
	 *
	 * Instead of performing any sort of name resolution, this method
	 * makes a `textDocument/definition` request to the ALS to get the
	 * element referenced by this element and returns it, or null if no
	 * such element was found or if something went wrong.
	 */
	@Nullable
	@Override
	public PsiElement resolve() {
		
		PsiFile  containingFile = getContainingFile();
		Document document       = getPsiFileDocument(containingFile);
		
		if (document == null) { return null; }
		
		String documentUri = containingFile.getVirtualFile().getUrl();
		
		// Make the request and wait for the result
		
		Location definitionLocation = AdaLSPDriver.getServer(getProject()).definition(
			documentUri, offsetToPosition(document, getStartOffset()));
		
		// If no valid result was returned, return null
		
		if (definitionLocation == null) { return null; }
		
		// Get the definition's file
		
		URL definitionFileUrl = urlStringToUrl(definitionLocation.getUri());
		
		if (definitionFileUrl == null) { return null; }
		
		VirtualFile definitionVirtualFile = VfsUtil.findFileByURL(definitionFileUrl);
		
		if (definitionVirtualFile == null) { return null; }
		
		PsiFile  definitionPsiFile  = getVirtualFilePsiFile(getProject(), definitionVirtualFile);
		Document definitionDocument = getVirtualFileDocument(definitionVirtualFile);
		
		if (definitionPsiFile == null || definitionDocument == null) { return null; }
		
		// Find the element at the given position in the file and return
		// it (will return null if the element is not found)
		
		return definitionPsiFile.findElementAt(
			positionToOffset(definitionDocument, definitionLocation.getRange().getStart()));
		
	}
	
	/**
	 * @see com.intellij.psi.PsiReference#getCanonicalText()
	 */
	@NotNull
	@Override
	public String getCanonicalText() {
		// TODO: Return proper canonical text here
		return node.getText();
	}
	
	/**
	 * @see com.intellij.psi.PsiReference#handleElementRename(String)
	 */
	@Override
	public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
		throw new IncorrectOperationException("Rename not yet supported");
	}
	
	/**
	 * @see com.intellij.psi.PsiReference#bindToElement(PsiElement)
	 */
	@Override
	public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
		throw new IncorrectOperationException("Bind not yet supported");
	}
	
	/**
	 * @see com.intellij.psi.PsiReference#isReferenceTo(PsiElement)
	 */
	@Override
	public boolean isReferenceTo(@NotNull PsiElement element) {
		return getText().toLowerCase().equals(element.getText().toLowerCase()) &&
			AdaPsiElement.areEqual(resolve(), element);
	}
	
	/**
	 * @see com.intellij.psi.PsiReference#getVariants()
	 */
	@NotNull
	@Override
	public Object[] getVariants() { return new Object[0]; }
	
	/**
	 * @see com.intellij.psi.PsiReference#isSoft()
	 */
	@Override
	public boolean isSoft() { return false; }
	
	/**
	 * @see com.intellij.pom.Navigatable#canNavigate()
	 */
	@Override
	public boolean canNavigate() { return true; }
	
	/**
	 * Returns whether or not this element references it self, i.e.
	 * if it is a declaration of any kind.
	 *
	 * @return Whether or not this element is a declaration.
	 */
	public boolean isDeclaration() {
		
		// TODO: Reimplement this method
		// A reference should actually be considered as a definition
		// iff `isReferenceTo(this)` returns true, which requires that,
		// for a declaration, `resolve` return the element itself
		
		return resolve() == null;
		
	}
	
}
