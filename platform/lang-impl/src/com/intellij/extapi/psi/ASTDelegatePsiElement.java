/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.extapi.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ASTDelegatePsiElement extends PsiElementBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.extapi.psi.ASTDelegatePsiElement");

  private static final List EMPTY = Collections.emptyList();

  public PsiManagerEx getManager() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return (PsiManagerEx)parent.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<PsiElement>();
    while (psiChild != null) {
      if (psiChild.getNode() instanceof CompositeElement) {
        result.add(psiChild);
      }
      psiChild = psiChild.getNextSibling();
    }
    return result.toArray(new PsiElement[result.size()]);
  }

  public PsiElement getFirstChild() {
    return SharedImplUtil.getFirstChild(getNode());
  }

  public PsiElement getLastChild() {
    return SharedImplUtil.getLastChild(getNode());
  }

  public PsiElement getNextSibling() {
    return SharedImplUtil.getNextSibling(getNode());
  }

  public PsiElement getPrevSibling() {
    return SharedImplUtil.getPrevSibling(getNode());
  }

  public TextRange getTextRange() {
    return getNode().getTextRange();
  }

  public int getStartOffsetInParent() {
    return getNode().getStartOffset() - getNode().getTreeParent().getStartOffset();
  }

  public int getTextLength() {
    return getNode().getTextLength();
  }

  public PsiElement findElementAt(int offset) {
    ASTNode treeElement = getNode().findLeafElementAt(offset);
    return SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public int getTextOffset() {
    return getNode().getStartOffset();
  }

  public String getText() {
    return getNode().getText();
  }

  @NotNull
  public char[] textToCharArray() {
    return getNode().getText().toCharArray();
  }

  public boolean textContains(char c) {
    return getNode().textContains(c);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getNode().getCopyableUserData(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    getNode().putCopyableUserData(key, value);
  }

  @NotNull
  public abstract ASTNode getNode();

  public void subtreeChanged() {
  }

  @NotNull
  public Language getLanguage() {
    return getNode().getElementType().getLanguage();
  }

  @Nullable
  protected PsiElement findChildByType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  @NotNull
  protected PsiElement findNotNullChildByType(IElementType type) {
    return notNullChild(findChildByType(type));
  }

  @Nullable
  protected PsiElement findChildByType(TokenSet type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : node.getPsi();
  }

  @NotNull
  protected PsiElement findNotNullChildByType(TokenSet type) {
    return notNullChild(findChildByType(type));
  }

  @Nullable
  protected PsiElement findChildByFilter(TokenSet tokenSet) {
    ASTNode[] nodes = getNode().getChildren(tokenSet);
    return nodes == null || nodes.length == 0 ? null : nodes[0].getPsi();
  }

  @NotNull
  protected PsiElement findNotNullChildByFilter(TokenSet tokenSet) {
    return notNullChild(findChildByFilter(tokenSet));
  }

  protected <T extends PsiElement> T[] findChildrenByType(IElementType elementType, Class<T> arrayClass) {
    return ContainerUtil.map2Array(SharedImplUtil.getChildrenOfType(getNode(), elementType), arrayClass, new Function<ASTNode, T>() {
      public T fun(final ASTNode s) {
        return (T)s.getPsi();
      }
    });
  }

  protected <T extends PsiElement> List<T> findChildrenByType(TokenSet elementType) {
    List<T> result = EMPTY;
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      final IElementType tt = child.getElementType();
      if (elementType.contains(tt)) {
        if (result == EMPTY) {
          result = new ArrayList<T>();
        }
        result.add((T)child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result;
  }

  protected <T extends PsiElement> List<T> findChildrenByType(IElementType elementType) {
    List<T> result = EMPTY;
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      if (elementType == child.getElementType()) {
        if (result == EMPTY) {
          result = new ArrayList<T>();
        }
        result.add((T)child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result;
  }

  protected <T extends PsiElement> T[] findChildrenByType(TokenSet elementType, Class<T> arrayClass) {
    return (T[])ContainerUtil.map2Array(getNode().getChildren(elementType), arrayClass, new Function<ASTNode, PsiElement>() {
      public PsiElement fun(final ASTNode s) {
        return s.getPsi();
      }
    });
  }

  public PsiElement copy() {
    return getNode().copyElement().getPsi();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return addInnerBefore(element, null);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return addInnerBefore(element, anchor);
  }

  private PsiElement addInnerBefore(final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
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
  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    return CodeEditUtil.addChildren(getNode(), first, last, getAnchorNode(anchor, before));
  }

  @Override
  public PsiElement addRange(final PsiElement first, final PsiElement last) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, null, null);
  }

  @Override
  public PsiElement addRangeBefore(@NotNull final PsiElement first, @NotNull final PsiElement last, final PsiElement anchor)
    throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.TRUE);
  }

  @Override
  public PsiElement addRangeAfter(final PsiElement first, final PsiElement last, final PsiElement anchor) throws IncorrectOperationException {
    return SharedImplUtil.addRange(this, first, last, SourceTreeToPsiMap.psiElementToTree(anchor), Boolean.FALSE);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    if (getParent() instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement) getParent()).deleteChildInternal(getNode());
    }
    else if (getParent() instanceof PsiFile) {
      CheckUtil.checkWritable(this);
      getParent().deleteChildRange(this, this);
    }
    else {
      throw new UnsupportedOperationException(getClass().getName() + " under " + (getParent() == null ? "null" : getParent().getClass().getName()));
    }
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    CodeEditUtil.removeChild(getNode(), child);
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
  public PsiElement replace(@NotNull final PsiElement newElement) throws IncorrectOperationException {
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

  private ASTNode getAnchorNode(final ASTNode anchor, final Boolean before) {
    ASTNode anchorBefore;
    if (anchor != null) {
      anchorBefore = before.booleanValue() ? anchor : anchor.getTreeNext();
    }
    else {
      if (before != null && !before.booleanValue()) {
        anchorBefore = getNode().getFirstChildNode();
      }
      else {
        anchorBefore = null;
      }
    }
    return anchorBefore;
  }
}
