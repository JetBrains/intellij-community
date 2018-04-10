// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class PackageAccessibilityStatementElement extends CompositeElement {
  public PackageAccessibilityStatementElement(@NotNull IElementType type) {
    super(type);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getElementType() == JavaElementType.MODULE_REFERENCE && anchor == null) {
      ASTNode lastChild = getLastChildNode(), addAfter = lastChild;
      if (lastChild.getElementType() == JavaTokenType.SEMICOLON || lastChild.getElementType() == TokenType.ERROR_ELEMENT) {
        addAfter = lastChild.getTreePrev();
        lastChild = PsiImplUtil.skipWhitespaceAndCommentsBack(addAfter);
      }
      if (lastChild != null) {
        CharTable charTable = SharedImplUtil.findCharTableByTree(this);
        if (lastChild.getElementType() == JavaElementType.JAVA_CODE_REFERENCE) {
          LeafElement to = Factory.createSingleLeafElement(JavaTokenType.TO_KEYWORD, PsiKeyword.TO, charTable, getManager());
          anchor = super.addInternal(to, to, addAfter, Boolean.FALSE);
          before = Boolean.FALSE;
        }
        else if (lastChild.getElementType() == JavaElementType.MODULE_REFERENCE) {
          LeafElement comma = Factory.createSingleLeafElement(JavaTokenType.COMMA, ",", charTable, getManager());
          anchor = super.addInternal(comma, comma, addAfter, Boolean.FALSE);
          before = Boolean.FALSE;
        }
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.MODULE_REFERENCE) {
      ASTNode comma = findNearestComma(child);
      if (comma != null) {
        super.deleteChildInternal(comma);
      }
      else {
        ASTNode toKeyword = findChildByType(JavaTokenType.TO_KEYWORD);
        if (toKeyword != null) {
          super.deleteChildInternal(toKeyword);
        }
      }
    }
    super.deleteChildInternal(child);
  }

  private static ASTNode findNearestComma(ASTNode child) {
    ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
    if (next != null && next.getElementType() == JavaTokenType.COMMA) {
      return next;
    }
    else {
      ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
      if (prev != null && prev.getElementType() == JavaTokenType.COMMA) {
        return prev;
      }
    }
    return null;
  }
}