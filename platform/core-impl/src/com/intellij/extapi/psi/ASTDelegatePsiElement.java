// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ASTDelegatePsiElement extends PsiElementBase {
  private static final Logger LOG = Logger.getInstance(ASTDelegatePsiElement.class);

  @Override
  public PsiFile getContainingFile() {
    PsiFile file = SharedImplUtil.getContainingFile(getNode());
    if (file == null) throw new PsiInvalidElementAccessException(this);
    return file;
  }

  @Override
  public PsiManagerEx getManager() {
    Project project = ProjectCoreUtil.theOnlyOpenProject();
    if (project != null) {
      return PsiManagerEx.getInstanceEx(project);
    }
    PsiElement parent = this;

    while (parent instanceof ASTDelegatePsiElement) {
      parent = parent.getParent();
    }

    if (parent == null) {
      throw new PsiInvalidElementAccessException(this);
    }

    return (PsiManagerEx)parent.getManager();
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return EMPTY_ARRAY;

    List<PsiElement> result = null;
    while (psiChild != null) {
      if (psiChild.getNode() instanceof CompositeElement) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(psiChild);
      }
      psiChild = psiChild.getNextSibling();
    }
    return result == null ? EMPTY_ARRAY : PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(getNode());
  }

  @Override
  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(getNode());
  }

  @Override
  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(getNode());
  }

  @Override
  public PsiElement getParent() {
    return SharedImplUtil.getParent(getNode());
  }

  @Override
  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(getNode());
  }

  @Override
  public TextRange getTextRange() {
    return getNode().getTextRange();
  }

  @Override
  public int getStartOffsetInParent() {
    return getNode().getStartOffset() - getNode().getTreeParent().getStartOffset();
  }

  @Override
  public int getTextLength() {
    return getNode().getTextLength();
  }

  @Override
  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = getNode().findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @Override
  public int getTextOffset() {
    return getNode().getStartOffset();
  }

  @Override
  public String getText() {
    return getNode().getText();
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return getNode().getText().toCharArray();
  }

  @Override
  public boolean textContains(char c) {
    return getNode().textContains(c);
  }

  @Override
  public <T> T getCopyableUserData(@NotNull Key<T> key) {
    return getNode().getCopyableUserData(key);
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, T value) {
    getNode().putCopyableUserData(key, value);
  }

  @Override
  public abstract @NotNull ASTNode getNode();

  public void subtreeChanged() {
  }

  @Override
  public @NotNull Language getLanguage() {
    return getNode().getElementType().getLanguage();
  }

  protected @Nullable <T extends PsiElement> T findChildByType(@NotNull IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    //noinspection unchecked
    return node == null ? null : (T)node.getPsi();
  }

  protected @Nullable <T extends PsiElement> T findLastChildByType(@NotNull IElementType type) {
    PsiElement child = getLastChild();
    while (child != null) {
      final ASTNode node = child.getNode();
      if (node != null && node.getElementType() == type) {
        //noinspection unchecked
        return (T)child;
      }
      child = child.getPrevSibling();
    }
    return null;
  }

  protected @NotNull <T extends PsiElement> T findNotNullChildByType(@NotNull IElementType type) {
    return notNullChild(findChildByType(type));
  }

  protected @Nullable <T extends PsiElement> T findChildByType(@NotNull TokenSet type) {
    ASTNode node = getNode().findChildByType(type);
    //noinspection unchecked
    return node == null ? null : (T)node.getPsi();
  }

  protected @NotNull <T extends PsiElement> T findNotNullChildByType(@NotNull TokenSet type) {
    return notNullChild(findChildByType(type));
  }

  /**
   * @deprecated Use {@link #findChildByType(TokenSet)} instead
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  protected @Nullable PsiElement findChildByFilter(@NotNull TokenSet tokenSet) {
    ASTNode[] nodes = getNode().getChildren(tokenSet);
    return nodes.length == 0 ? null : nodes[0].getPsi();
  }

  protected <T extends PsiElement> T @NotNull [] findChildrenByType(@NotNull IElementType elementType, @NotNull Class<T> arrayClass) {
    //noinspection unchecked
    return ContainerUtil.map2Array(SharedImplUtil.getChildrenOfType(getNode(), elementType), arrayClass, s -> (T)s.getPsi());
  }

  protected @Unmodifiable <T extends PsiElement> @NotNull List<T> findChildrenByType(@NotNull TokenSet elementType) {
    List<T> result = Collections.emptyList();
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      final IElementType tt = child.getElementType();
      if (elementType.contains(tt)) {
        if (result == Collections.<T>emptyList()) {
          result = new ArrayList<>();
        }
        //noinspection unchecked
        result.add((T)child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result;
  }

  protected @Unmodifiable <T extends PsiElement> @NotNull List<T> findChildrenByType(@NotNull IElementType elementType) {
    List<T> result = Collections.emptyList();
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      if (elementType == child.getElementType()) {
        if (result == Collections.<T>emptyList()) {
          result = new ArrayList<>();
        }
        //noinspection unchecked
        result.add((T)child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result;
  }

  protected <T extends PsiElement> T @NotNull [] findChildrenByType(@NotNull TokenSet elementType, @NotNull Class<T> arrayClass) {
    //noinspection unchecked
    return ContainerUtil.map2Array(getNode().getChildren(elementType), arrayClass, s -> (T)s.getPsi());
  }

  @Override
  public PsiElement copy() {
    return getNode().copyElement().getPsi();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return addInnerBefore(element, null);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return addInnerBefore(element, anchor);
  }

  private PsiElement addInnerBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    ASTNode treeElement = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
    if (treeElement != null) {
      if (treeElement instanceof TreeElement) {
        return ChangeUtil.decodeInformation((TreeElement) treeElement).getPsi();
      }
      return treeElement.getPsi();
    }
    throw new IncorrectOperationException("Element cannot be added");
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(element);
    ASTNode treeElement = addInternal(elementCopy, elementCopy, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
    if (treeElement instanceof TreeElement) {
      return ChangeUtil.decodeInformation((TreeElement) treeElement).getPsi();
    }
    return treeElement.getPsi();
  }

  @Override
  public void checkAdd(final @NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public ASTNode addInternal(@NotNull ASTNode first, @NotNull ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
    return CodeEditUtil.addChildren(getNode(), first, last, getAnchorNode(anchor, before));
  }

  @Override
  public PsiElement addRange(final PsiElement first, final PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  @Override
  public PsiElement addRangeBefore(final @NotNull PsiElement first, final @NotNull PsiElement last, final PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  @Override
  public PsiElement addRangeAfter(final PsiElement first, final PsiElement last, final PsiElement anchor) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    if (parent instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement)parent).deleteChildInternal(((PsiElement)this).getNode());
    }
    else if (parent instanceof CompositeElement) {
      CheckUtil.checkWritable(this);
      ((CompositeElement)parent).deleteChildInternal(((PsiElement)this).getNode());
    }
    else if (parent instanceof PsiFile) {
      CheckUtil.checkWritable(this);
      parent.deleteChildRange(this, this);
    }
    else {
      throw new UnsupportedOperationException(
        ((PsiElement)this).getClass().getName() + " under " + (parent == null ? "null" : parent.getClass().getName()));
    }
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    ((CompositeElement)getNode()).deleteChildInternal(child);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  @Override
  public void deleteChildRange(final PsiElement first, final PsiElement last) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    ASTNode firstElement = SourceTreeToPsiMap.psiElementToTree(first);
    ASTNode lastElement = SourceTreeToPsiMap.psiElementToTree(last);

    LOG.assertTrue(firstElement.getTreeParent() == getNode());
    LOG.assertTrue(lastElement.getTreeParent() == getNode());
    CodeEditUtil.removeChildren(getNode(), firstElement, lastElement);
  }

  @Override
  public PsiElement replace(final @NotNull PsiElement newElement) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    if (getParent() instanceof ASTDelegatePsiElement) {
      final ASTDelegatePsiElement parentElement = (ASTDelegatePsiElement)getParent();
      parentElement.replaceChildInternal(this, elementCopy);
    }
    else {
      CodeEditUtil.replaceChild(getParent().getNode(), getNode(), elementCopy);
    }
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    return SourceTreeToPsiMap.treeElementToPsi(elementCopy);
  }

  public void replaceChildInternal(final PsiElement child, final TreeElement newElement) {
    CodeEditUtil.replaceChild(getNode(), child.getNode(), newElement);
  }

  private @Nullable ASTNode getAnchorNode(@Nullable ASTNode anchor, @Nullable Boolean before) {
    assert anchor == null || before != null;

    if (anchor != null) {
      return before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    else if (before != null && !before.booleanValue()) {
      return getNode().getFirstChildNode();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    ASTNode node = getNode();
    return node instanceof TreeElement ? ((TreeElement)node).textMatches(text) : super.textMatches(text);
  }
}
