// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class CompositePsiElement extends CompositeElement implements NavigatablePsiElement {
  private static final Logger LOG = Logger.getInstance(CompositePsiElement.class);

  protected static int ourHC;

  protected CompositePsiElement(IElementType type) {
    super(type);
    setPsi(this);
  }

  @NotNull
  @Override
  public CompositePsiElement clone() {
    CompositePsiElement clone = (CompositePsiElement)super.clone();
    clone.setPsi(clone);
    return clone;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return getChildrenAsPsiElements((TokenSet)null, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement getFirstChild() {
    ASTNode node = getFirstChildNode();
    return node != null ? node.getPsi() : null;
  }

  @Override
  public PsiElement getLastChild() {
    ASTNode node = getLastChildNode();
    return node != null ? node.getPsi() : null;
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      child.accept(visitor);
      child = child.getNextSibling();
    }
  }

  @Override
  public PsiElement getParent() {
    CompositeElement parentNode = getTreeParent();
    return parentNode != null ? parentNode.getPsi() : null;
  }

  @Override
  public PsiElement getNextSibling() {
    ASTNode node = getTreeNext();
    return node != null ? node.getPsi() : null;
  }

  @Override
  public PsiElement getPrevSibling() {
    ASTNode node = getTreePrev();
    return node != null ? node.getPsi() : null;
  }

  @Override
  public PsiFile getContainingFile() {
    PsiFile file = SharedImplUtil.getContainingFile(this);
    if (file == null) throw new PsiInvalidElementAccessException(this);
    return file;
  }

  @Override
  public PsiElement findElementAt(int offset) {
    ASTNode leaf = findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(leaf);
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  @Override
  public PsiElement copy() {
    ASTNode elementCopy = copyElement();
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  @Override
  public boolean isValid() {
    return SharedImplUtil.isValid(this);
  }

  @Override
  public boolean isWritable() {
    return SharedImplUtil.isWritable(this);
  }

  @Override
  public PsiReference getReference() {
    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return addInnerBefore(element, null);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return addInnerBefore(element, anchor);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    TreeElement treeElement = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    return ChangeUtil.decodeInformation(treeElement).getPsi();
  }

  @Override
  public final void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  @Override
  public final PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  @Override
  public final PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  @Override
  public final PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    LOG.assertTrue(getTreeParent() != null, "Parent not found for " + this);
    CheckUtil.checkWritable(this);
    getTreeParent().deleteChildInternal(this);
    invalidate();
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    ASTNode firstElement = first.getNode();
    ASTNode lastElement = last.getNode();
    LOG.assertTrue(firstElement.getTreeParent() == this);
    LOG.assertTrue(lastElement.getTreeParent() == this);
    CodeEditUtil.removeChildren(this, firstElement, lastElement);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return SharedImplUtil.doReplace(this, this, newElement);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) { //TODO: remove this method!!
    visitor.visitElement(this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  @Override
  public String toString() {
    return "PsiElement" + "(" + getElementType() + ")";
  }

  @Override
  public PsiElement getContext() {
    return getParent();
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public boolean isPhysical() {
    PsiFile file = getContainingFile();
    return file != null && file.isPhysical();
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ResolveScopeManager.getElementResolveScope(this);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  @Override
  public ItemPresentation getPresentation() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(this);
    if (descriptor != null) descriptor.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return PsiNavigationSupport.getInstance().canNavigate(this);
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  @NotNull
  public Project getProject() {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return project;
    }
    PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return getElementType().getLanguage();
  }

  @Override
  @NotNull
  public ASTNode getNode() {
    return this;
  }

  private PsiElement addInnerBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    TreeElement treeElement = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    if (treeElement != null) return ChangeUtil.decodeInformation(treeElement).getPsi();
    throw new IncorrectOperationException("Element cannot be added");
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return this == another;
  }
}

