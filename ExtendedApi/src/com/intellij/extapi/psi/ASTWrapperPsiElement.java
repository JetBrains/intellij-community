package com.intellij.extapi.psi;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.Navigatable;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2005
 * Time: 2:54:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class ASTWrapperPsiElement extends ElementBase implements PsiElement, NavigationItem {
  private ASTNode myNode;

  public ASTWrapperPsiElement(final ASTNode node) {
    myNode = node;
  }

  public Project getProject() {
    final PsiManager manager = getManager();
    return manager != null ? manager.getProject() : null;
  }

  public PsiManager getManager() {
    final PsiElement parent = getParent();
    return parent != null ? parent.getManager() : null;
  }

  @NotNull
  public PsiElement[] getChildren() {
    List<PsiElement> result = new ArrayList<PsiElement>();
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      if (child instanceof CompositeElement) {
        result.add(child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public PsiElement getParent() {
    return SharedImplUtil.getParent(myNode);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(myNode);
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(myNode);
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(myNode);
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(myNode);
  }

  public PsiFile getContainingFile() {
    final PsiElement parent = getParent();
    return (parent != null)?parent.getContainingFile():null;
  }

  public TextRange getTextRange() {
    return myNode.getTextRange();
  }

  public int getStartOffsetInParent() {
    return myNode.getStartOffset() - myNode.getTreeParent().getStartOffset();
  }

  public int getTextLength() {
    return myNode.getTextLength();
  }

  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = myNode.findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public int getTextOffset() {
    return myNode.getStartOffset();
  }

  public String getText() {
    return myNode.getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return myNode.getText().toCharArray();
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  //Q: get rid of these methods?
  public boolean textMatches(CharSequence text) {
    return Comparing.equal(getText(), text, false);
  }

  public boolean textMatches(PsiElement element) {
    return getText().equals(element.getText());
  }

  public boolean textContains(char c) {
    return myNode.textContains(c);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while(child != null) {
      child.accept(visitor);
      child = child.getNextSibling();
    }
  }

  public PsiElement copy() {
    return (PsiElement)clone();
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {

    throw new UnsupportedOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    final PsiFile containingFile = getContainingFile();
    return (containingFile!=null)?containingFile.isValid():false;
  }

  public boolean isWritable() {
    return getContainingFile().isWritable();
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return ResolveUtil.getContext(this);
  }

  public boolean isPhysical() {
    return getContainingFile().isPhysical();
  }

  public GlobalSearchScope getResolveScope() {
    return ((PsiManagerImpl)getManager()).getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return getManager().getSearchHelper().getUseScope(this);
  }

  public ItemPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    final Navigatable descriptor = EditSourceUtil.getDescriptor(this);
    return descriptor != null && descriptor.canNavigateToSource();
  }

  public FileStatus getFileStatus() {
    if (!isPhysical()) return FileStatus.NOT_CHANGED;
    PsiFile contFile = getContainingFile();
    if (contFile == null) return FileStatus.NOT_CHANGED;
    VirtualFile vFile = contFile.getVirtualFile();
    return vFile != null ? FileStatusManager.getInstance(getProject()).getStatus(vFile) : FileStatus.NOT_CHANGED;
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return myNode.getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    myNode.putCopyableUserData(key, value);
  }

  public ASTNode getNode() {
    return myNode;
  }

  @NotNull
  public Language getLanguage() {
    return myNode.getElementType().getLanguage();
  }
}
