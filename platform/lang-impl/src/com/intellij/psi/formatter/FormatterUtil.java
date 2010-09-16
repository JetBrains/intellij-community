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
package com.intellij.psi.formatter;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FormatterUtil {
  private static final List<FormatterUtilHelper> ourHelpers = ContainerUtil.createEmptyCOWList();

  private FormatterUtil() {
  }

  public static void addHelper(FormatterUtilHelper helper) {
    ourHelpers.add(helper);
  }

  private static ASTNode getWsCandidate(ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      if (isSpaceTextElement(treePrev)) {
        return treePrev;
      }
      else if (treePrev.getTextLength() == 0) {
        return getWsCandidate(treePrev);
      }
      else {
        return element;
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return element;
    }
    else {
      return getWsCandidate(treeParent);
    }
  }

  @Nullable
  private static ASTNode getLastChildOf(ASTNode element) {
    return TreeUtil.getLastChild(element);
  }

  private static boolean isWhiteSpaceElement(ASTNode treePrev) {
    return isWhiteSpaceElement(treePrev, TokenType.WHITE_SPACE);
  }

  private static boolean isWhiteSpaceElement(ASTNode treePrev, IElementType whiteSpaceTokenType) {
    return treePrev.getElementType() == whiteSpaceTokenType;
  }

  private static boolean isSpaceTextElement(ASTNode treePrev) {
    return isWhiteSpaceElement(treePrev);
  }

  public static void replaceWhiteSpace(final String whiteSpace,
                                         final ASTNode leafElement,
                                         final IElementType whiteSpaceToken,
                                         final @Nullable TextRange textRange) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(leafElement);

    if (textRange != null && textRange.getStartOffset() > leafElement.getTextRange().getStartOffset() &&
        textRange.getEndOffset() < leafElement.getTextRange().getEndOffset()) {
      StringBuilder newText = createNewLeafChars(leafElement, textRange, whiteSpace);
      LeafElement newElement = Factory.createSingleLeafElement(leafElement.getElementType(), newText, charTable, leafElement.getPsi().getManager());

      leafElement.getTreeParent().replaceChild(leafElement, newElement);
      return;
    }

    ASTNode treePrev = findPreviousWhiteSpace(leafElement, whiteSpaceToken);
    if (treePrev == null) {
      treePrev = getWsCandidate(leafElement);
    }

    if (treePrev != null &&
        treePrev.getText().trim().length() == 0 &&
        treePrev.getElementType() != whiteSpaceToken &&
        treePrev.getTextLength() > 0 &&
        whiteSpace.length() >
        0) {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(treePrev.getElementType(), whiteSpace, charTable, SharedImplUtil.getManagerByTree(leafElement));

      ASTNode treeParent = treePrev.getTreeParent();
      treeParent.replaceChild(treePrev, whiteSpaceElement);
    }
    else {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(whiteSpaceToken, whiteSpace, charTable, SharedImplUtil.getManagerByTree(leafElement));

      if (treePrev == null) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(leafElement, whiteSpaceElement);
        }
      }
      else if (!isWhiteSpaceElement(treePrev, whiteSpaceToken)) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(treePrev, whiteSpaceElement);
        }
      }
      else if (isWhiteSpaceElement(treePrev, whiteSpaceToken)) {
        final CompositeElement treeParent = (CompositeElement)treePrev.getTreeParent();
        if (whiteSpace.length() > 0) {
//          LOG.assertTrue(textRange == null || treeParent.getTextRange().equals(textRange));
          treeParent.replaceChild(treePrev, whiteSpaceElement);
        }
        else {
          treeParent.removeChild(treePrev);
        }
        //treeParent.subtreeChanged();
      }
    }
  }

  private static StringBuilder createNewLeafChars(final ASTNode leafElement, final TextRange textRange, final String whiteSpace) {
    final TextRange elementRange = leafElement.getTextRange();
    final String elementText = leafElement.getText();

    final StringBuilder result = new StringBuilder();

    if (elementRange.getStartOffset() < textRange.getStartOffset()) {
      result.append(elementText.substring(0, textRange.getStartOffset() - elementRange.getStartOffset()));
    }

    result.append(whiteSpace);

    if (elementRange.getEndOffset() > textRange.getEndOffset()) {
      result.append(elementText.substring(textRange.getEndOffset() - elementRange.getStartOffset()));
    }

    return result;
  }

  private static void addWhiteSpace(final ASTNode treePrev, final LeafElement whiteSpaceElement) {
    for (FormatterUtilHelper helper : ourHelpers) {
      if (helper.addWhitespace(treePrev, whiteSpaceElement)) return;
    }

    final ASTNode treeParent = treePrev.getTreeParent();
    treeParent.addChild(whiteSpaceElement, treePrev);
  }

  @Nullable
  private static ASTNode findPreviousWhiteSpace(final ASTNode leafElement, final IElementType whiteSpaceTokenType) {
    final int offset = leafElement.getTextRange().getStartOffset() - 1;
    if (offset < 0) return null;
    final PsiElement found = SourceTreeToPsiMap.treeElementToPsi(leafElement).getContainingFile().findElementAt(offset);
    if (found == null) return null;
    final ASTNode treeElement = found.getNode();
    if (treeElement != null && treeElement.getElementType() == whiteSpaceTokenType) return treeElement;
    return null;
  }

  @Nullable
  public static ASTNode getLeafNonSpaceBefore(final ASTNode element) {
    if (element == null) return null;
    ASTNode treePrev = element.getTreePrev();
    if (treePrev != null) {
      ASTNode candidate = getLastChildOf(treePrev);
      if (candidate != null && !isSpaceTextElement(candidate) && candidate.getTextLength() > 0) {
        return candidate;
      }
      else {
        return getLeafNonSpaceBefore(candidate);
      }
    }
    final ASTNode treeParent = element.getTreeParent();

    if (treeParent == null || treeParent.getTreeParent() == null) {
      return null;
    }
    else {
      return getLeafNonSpaceBefore(treeParent);
    }

  }

  public static ASTNode getPreviousNonSpaceSibling(ASTNode node) {
    ASTNode prevNode = node.getTreePrev();
    while (prevNode != null && prevNode.getPsi() instanceof PsiWhiteSpace) {
      prevNode = prevNode.getTreePrev();
    }
    return prevNode;
  }


  public static boolean isIncompleted(final ASTNode treeNode) {
    ASTNode lastChild = treeNode.getLastChildNode();
    while (lastChild != null && lastChild.getElementType() == TokenType.WHITE_SPACE) {
      lastChild = lastChild.getTreePrev();
    }
    if (lastChild == null) return false;
    if (lastChild.getElementType() == TokenType.ERROR_ELEMENT) return true;
    return isIncompleted(lastChild);
  }

  public static void replaceLastWhiteSpace(final ASTNode astNode, final String whiteSpace, final TextRange textRange) {
    LeafElement lastWS = TreeUtil.findLastLeaf(astNode);
    if (lastWS.getElementType() != TokenType.WHITE_SPACE) {
      lastWS = null;
    }
    if (lastWS != null && !lastWS.getTextRange().equals(textRange)) {
      return;
    }
    if (whiteSpace.length() == 0 && lastWS == null) {
      return;
    }
    if (lastWS != null && whiteSpace.length() == 0) {
      lastWS.getTreeParent().removeRange(lastWS, null);
      return;
    }

    LeafElement whiteSpaceElement = ASTFactory.whitespace(whiteSpace);

    if (lastWS == null) {
      astNode.addChild(whiteSpaceElement, null);
    } else {
      ASTNode treeParent = lastWS.getTreeParent();
      treeParent.replaceChild(lastWS, whiteSpaceElement);
    }
  }

  public static boolean containsWhiteSpacesOnly(final ASTNode node) {
    if (node.getElementType() == TokenType.WHITE_SPACE || node.getTextLength() == 0) return true;
    for (FormatterUtilHelper helper : ourHelpers) {
      if (helper.containsWhitespacesOnly(node)) return true;
    }

    if (node instanceof LeafElement) return false;
    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      if (!containsWhiteSpacesOnly(child)) return false;
      child = child.getTreeNext();
    }
    return true;
  }

  public static boolean isPrecededBy(ASTNode node, IElementType eType) {
    ASTNode prevNode = node.getTreePrev();
    while (prevNode != null && prevNode.getPsi() instanceof PsiWhiteSpace) {
      prevNode = prevNode.getTreePrev();
    }
    if (prevNode == null) return false;
    return prevNode.getElementType() == eType;
  }

  public static boolean isPrecededBy(ASTNode node, TokenSet tokens) {
    ASTNode prevNode = node.getTreePrev();
    while (prevNode != null && prevNode.getPsi() instanceof PsiWhiteSpace) {
      prevNode = prevNode.getTreePrev();
    }
    if (prevNode == null) return false;
    return tokens.contains(prevNode.getElementType());
  }

  public static boolean isFollowedBy(ASTNode node, IElementType eType) {
    ASTNode nextNode = node.getTreeNext();
    while (nextNode != null && nextNode.getPsi() instanceof PsiWhiteSpace) {
      nextNode = nextNode.getTreeNext();
    }
    if (nextNode == null) return false;
    return nextNode.getElementType() == eType;
  }
}
