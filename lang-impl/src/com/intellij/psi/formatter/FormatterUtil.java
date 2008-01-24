/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FormatterUtil {
  private static List<FormatterUtilHelper> ourHelpers = new CopyOnWriteArrayList<FormatterUtilHelper>();

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

  private static ASTNode getLastChildOf(ASTNode element) {
    if (element == null) {
      return null;
    }
    if (element instanceof LeafElement) {
      return element;
    }
    else {
      final ASTNode node = element.getLastChildNode();
      if (node instanceof LeafElement) ChameleonTransforming.transform((LeafElement)node);
      final ASTNode lastChild = element.getLastChildNode();
      if (lastChild == null) {
        return element;
      }
      else {
        return getLastChildOf(lastChild);
      }
    }
  }

  private static boolean isWhiteSpaceElement(ASTNode treePrev) {
    return treePrev.getElementType() == TokenType.WHITE_SPACE;
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
      LeafElement newElement = Factory.createSingleLeafElement(leafElement.getElementType(),
                                                               newText,
                                                               0, newText.length(), charTable, leafElement.getPsi().getManager());

      leafElement.getTreeParent().replaceChild(leafElement, newElement);
      return;
    }

    ASTNode treePrev = findPreviousWhiteSpace(leafElement);
    if (treePrev == null) {
      treePrev = getWsCandidate(leafElement);
    }

    if (treePrev != null &&
        treePrev.getText().trim().length() == 0 &&
        treePrev.getElementType() != whiteSpaceToken &&
        treePrev.getTextLength() > 0 &&
        whiteSpace.length() >
        0) {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(treePrev.getElementType(), whiteSpace, 0, whiteSpace.length(),
                                                                      charTable, SharedImplUtil.getManagerByTree(leafElement));

      ASTNode treeParent = treePrev.getTreeParent();
      treeParent.replaceChild(treePrev, whiteSpaceElement);
    }
    else {
      LeafElement whiteSpaceElement = Factory.createSingleLeafElement(whiteSpaceToken, whiteSpace, 0, whiteSpace.length(),
                                                                      charTable, SharedImplUtil.getManagerByTree(leafElement));

      if (treePrev == null) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(leafElement, whiteSpaceElement);
        }
      }
      else if (!isSpaceTextElement(treePrev)) {
        if (whiteSpace.length() > 0) {
          addWhiteSpace(treePrev, whiteSpaceElement);
        }
      }
      else if (isWhiteSpaceElement(treePrev)) {
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
  private static ASTNode findPreviousWhiteSpace(final ASTNode leafElement) {
    final int offset = leafElement.getTextRange().getStartOffset() - 1;
    if (offset < 0) return null;
    final PsiElement found = SourceTreeToPsiMap.treeElementToPsi(leafElement).getContainingFile().findElementAt(offset);
    if (found == null) return null;
    final ASTNode treeElement = found.getNode();
    if (treeElement.getElementType() == TokenType.WHITE_SPACE) return treeElement;
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
    LeafElement whiteSpaceElement = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, whiteSpace, 0, whiteSpace.length(),
                                                                    SharedImplUtil.findCharTableByTree(astNode), SharedImplUtil.getManagerByTree(astNode));

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

}
