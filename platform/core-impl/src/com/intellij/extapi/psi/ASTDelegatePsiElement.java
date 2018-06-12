/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ASTDelegatePsiElement extends PsiElementBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.extapi.psi.ASTDelegatePsiElement");

  private static final List EMPTY = Collections.emptyList();

  @Override
  public PsiFile getContainingFile() {
    return SharedImplUtil.getContainingFile(getNode());
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
  @NotNull
  public PsiElement[] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<>();
    while (psiChild != null) {
      if (psiChild.getNode() instanceof CompositeElement) {
        result.add(psiChild);
      }
      psiChild = psiChild.getNextSibling();
    }
    return PsiUtilCore.toPsiElementArray(result);
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
  @NotNull
  public char[] textToCharArray() {
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
  @NotNull
  public abstract ASTNode getNode();

  public void subtreeChanged() {
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return getNode().getElementType().getLanguage();
  }

  @Nullable
  protected <T extends PsiElement> T findChildByType(IElementType type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : (T)node.getPsi();
  }

  @Nullable
  protected <T extends PsiElement> T findLastChildByType(IElementType type) {
    PsiElement child = getLastChild();
    while (child != null) {
      final ASTNode node = child.getNode();
      if (node != null && node.getElementType() == type) return (T)child;
      child = child.getPrevSibling();
    }
    return null;
  }



  @NotNull
  protected <T extends PsiElement> T findNotNullChildByType(IElementType type) {
    return notNullChild(this.<T>findChildByType(type));
  }

  @Nullable
  protected <T extends PsiElement> T findChildByType(TokenSet type) {
    ASTNode node = getNode().findChildByType(type);
    return node == null ? null : (T)node.getPsi();
  }

  @NotNull
  protected <T extends PsiElement> T findNotNullChildByType(TokenSet type) {
    return notNullChild(this.<T>findChildByType(type));
  }

  @Nullable
  protected PsiElement findChildByFilter(TokenSet tokenSet) {
    ASTNode[] nodes = getNode().getChildren(tokenSet);
    return nodes.length == 0 ? null : nodes[0].getPsi();
  }

  @NotNull
  protected <T extends PsiElement> T[] findChildrenByType(IElementType elementType, Class<T> arrayClass) {
    return ContainerUtil.map2Array(SharedImplUtil.getChildrenOfType(getNode(), elementType), arrayClass, s -> (T)s.getPsi());
  }

  protected <T extends PsiElement> List<T> findChildrenByType(TokenSet elementType) {
    List<T> result = EMPTY;
    ASTNode child = getNode().getFirstChildNode();
    while (child != null) {
      final IElementType tt = child.getElementType();
      if (elementType.contains(tt)) {
        if (result == EMPTY) {
          result = new ArrayList<>();
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
          result = new ArrayList<>();
        }
        result.add((T)child.getPsi());
      }
      child = child.getTreeNext();
    }
    return result;
  }

  @NotNull
  protected <T extends PsiElement> T[] findChildrenByType(TokenSet elementType, Class<T> arrayClass) {
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
    PsiElement parent = getParent();
    if (parent instanceof ASTDelegatePsiElement) {
      CheckUtil.checkWritable(this);
      ((ASTDelegatePsiElement)parent).deleteChildInternal(getNode());
    }
    else if (parent instanceof CompositeElement) {
      CheckUtil.checkWritable(this);
      ((CompositeElement)parent).deleteChildInternal(getNode());
    }
    else if (parent instanceof PsiFile) {
      CheckUtil.checkWritable(this);
      parent.deleteChildRange(this, this);
    }
    else {
      throw new UnsupportedOperationException(getClass().getName() + " under " + (parent == null ? "null" : parent.getClass().getName()));
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

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    ASTNode node = getNode();
    return node instanceof TreeElement ? ((TreeElement)node).textMatches(text) : super.textMatches(text);
  }
}
