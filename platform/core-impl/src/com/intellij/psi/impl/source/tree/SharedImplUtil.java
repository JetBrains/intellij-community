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

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//TODO: rename/regroup?

public class SharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SharedImplUtil");
  private static final boolean CHECK_FOR_READ_ACTION = DebugUtil.DO_EXPENSIVE_CHECKS || ApplicationManager.getApplication().isInternal();

  private SharedImplUtil() {
  }

  public static PsiElement getParent(ASTNode thisElement) {
    if (CHECK_FOR_READ_ACTION && thisElement instanceof TreeElement) {
      ((TreeElement)thisElement).assertReadAccessAllowed();
    }
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeParent());
  }

  public static PsiElement getFirstChild(@NotNull ASTNode element) {
    return SourceTreeToPsiMap.treeElementToPsi(element.getFirstChildNode());
  }

  @Nullable
  public static PsiElement getLastChild(@NotNull ASTNode element) {
    return SourceTreeToPsiMap.treeElementToPsi(element.getLastChildNode());
  }

  public static PsiElement getNextSibling(@NotNull ASTNode thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeNext());
  }

  public static PsiElement getPrevSibling(@NotNull ASTNode thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreePrev());
  }

  public static PsiFile getContainingFile(@NotNull ASTNode thisElement) {
    FileASTNode node = findFileElement(thisElement);
    PsiElement psi = node == null ? null : node.getPsi();
    if (psi == null || psi instanceof PsiFile) return (PsiFile)psi;
    throw new AssertionError("Invalid PSI " + psi + " of " + psi.getClass() + " for AST " + node + " of " + node.getClass());
  }

  public static boolean isValid(ASTNode thisElement) {
    LOG.assertTrue(thisElement instanceof PsiElement);
    PsiFile file = getContainingFile(thisElement);
    return file != null && file.isValid();
  }

  public static boolean isWritable(ASTNode thisElement) {
    PsiFile file = getContainingFile(thisElement);
    return file == null || file.isWritable();
  }

  public static FileASTNode findFileElement(@NotNull ASTNode element) {
    ASTNode parent = element.getTreeParent();
    while (parent != null) {
      element = parent;
      parent = parent.getTreeParent();
    }

    if (CHECK_FOR_READ_ACTION && element instanceof TreeElement) {
      ((TreeElement)element).assertReadAccessAllowed();
    }

    if (element instanceof FileASTNode) {
      return (FileASTNode)element;
    }
    return null;
  }

  @NotNull
  public static CharTable findCharTableByTree(ASTNode tree) {
    for (ASTNode o = tree; o != null; o = o.getTreeParent()) {
      CharTable charTable = o.getUserData(CharTable.CHAR_TABLE_KEY);
      if (charTable != null) {
        return charTable;
      }
      if (o instanceof FileASTNode) {
        return ((FileASTNode)o).getCharTable();
      }
    }
    throw new AssertionError("CharTable not found in: " + tree);
  }

  public static PsiElement addRange(PsiElement thisElement,
                                    PsiElement first,
                                    PsiElement last,
                                    ASTNode anchor,
                                    Boolean before) throws IncorrectOperationException {
    CheckUtil.checkWritable(thisElement);
    final CharTable table = findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(thisElement));

    TreeElement copyFirst = null;
    ASTNode copyLast = null;
    ASTNode next = SourceTreeToPsiMap.psiElementToTree(last).getTreeNext();
    ASTNode parent = null;
    for (ASTNode element = SourceTreeToPsiMap.psiElementToTree(first); element != next; element = element.getTreeNext()) {
      TreeElement elementCopy = ChangeUtil.copyElement((TreeElement)element, table);
      if (element == first.getNode()) {
        copyFirst = elementCopy;
      }
      if (element == last.getNode()) {
        copyLast = elementCopy;
      }
      if (parent == null) {
        parent = elementCopy.getTreeParent();
      }
      else {
        if(elementCopy.getElementType() == TokenType.WHITE_SPACE)
          CodeEditUtil.setNodeGenerated(elementCopy, true);
        parent.addChild(elementCopy, null);
      }
    }
    if (copyFirst == null) return null;
    copyFirst = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(thisElement)).addInternal(copyFirst, copyLast, anchor, before);
    for (TreeElement element = copyFirst; element != null; element = element.getTreeNext()) {
      element = ChangeUtil.decodeInformation(element);
      if (element.getTreePrev() == null) {
        copyFirst = element;
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(copyFirst);
  }

  public static PsiManager getManagerByTree(final ASTNode node) {
    if(node instanceof FileElement) return node.getPsi().getManager();
    return node.getTreeParent().getPsi().getManager();
  }

  @NotNull
  public static ASTNode[] getChildrenOfType(@NotNull ASTNode node, @NotNull IElementType elementType) {
    int count = countChildrenOfType(node, elementType);
    if (count == 0) {
      return ASTNode.EMPTY_ARRAY;
    }
    final ASTNode[] result = new ASTNode[count];
    count = 0;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == elementType) {
        result[count++] = child;
      }
    }
    return result;
  }

  private static int countChildrenOfType(@NotNull ASTNode node, @NotNull IElementType elementType) {
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getElementType() == elementType) {
        count++;
      }
    }

    return count;
  }

  public static void acceptChildren(@NotNull PsiElementVisitor visitor, @NotNull ASTNode root) {
    ASTNode childNode = root.getFirstChildNode();

    while (childNode != null) {
      final PsiElement psi;
      if (childNode instanceof PsiElement) {
        psi = (PsiElement)childNode;
      }
      else {
        psi = childNode.getPsi();
      }
      psi.accept(visitor);

      childNode = childNode.getTreeNext();
    }
  }

  public static PsiElement doReplace(@NotNull PsiElement psiElement, @NotNull TreeElement treeElement, @NotNull PsiElement newElement) {
    CompositeElement treeParent = treeElement.getTreeParent();
    LOG.assertTrue(treeParent != null);
    CheckUtil.checkWritable(psiElement);
    TreeElement elementCopy = ChangeUtil.copyToElement(newElement);
    treeParent.replaceChildInternal(treeElement, elementCopy);
    elementCopy = ChangeUtil.decodeInformation(elementCopy);
    final PsiElement result = SourceTreeToPsiMap.treeElementToPsi(elementCopy);
    treeElement.invalidate();
    return result;
  }
}
