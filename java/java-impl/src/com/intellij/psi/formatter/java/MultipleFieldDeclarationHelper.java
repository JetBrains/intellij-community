// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import org.jetbrains.annotations.NotNull;

public final class MultipleFieldDeclarationHelper {

  /**
   * @return {@code true} if given node is a non-first part of composite field definition; {@code false} otherwise
   */
  public static boolean compoundFieldPart(@NotNull ASTNode node) {
    if (node.getElementType() != JavaElementType.FIELD) {
      return false;
    }
    ASTNode firstChild = node.getFirstChildNode();
    if (firstChild == null || firstChild.getElementType() != JavaTokenType.IDENTIFIER) {
      return false;
    }

    ASTNode prev = node.getTreePrev();
    return prev == null || !JavaJspElementType.WHITE_SPACE_BIT_SET.contains(prev.getElementType())
           || StringUtil.countNewLines(prev.getChars()) <= 1;
  }

  /**
   * Serves for processing composite field definitions as a single formatting block.
   * <p/>
   * {@code 'Composite field definition'} looks like {@code 'int i1, i2 = 2'}. It produces two nodes of type
   * {@link JavaElementType#FIELD} - {@code 'int i1'} and {@code 'i2 = 2'}. This method returns the second node if the first one
   * is given (the given node is returned for {@code 'single'} fields).
   *
   * @param child     child field node to check
   * @return          last child field node at the field group identified by the given node if any; given child otherwise
   */
  public static @NotNull ASTNode findLastFieldInGroup(final @NotNull ASTNode child) {
    PsiElement psi = child.getPsi();
    if (psi == null) {
      return child;
    }
    final PsiTypeElement typeElement = ((PsiVariable)psi).getTypeElement();
    if (typeElement == null) return child;

    ASTNode lastChildNode = child.getLastChildNode();
    if (lastChildNode == null) return child;

    if (lastChildNode.getElementType() == JavaTokenType.SEMICOLON) return child;

    ASTNode currentResult = child;

    for (ASTNode currentNode = child.getTreeNext(); currentNode != null; currentNode = currentNode.getTreeNext()) {
      if (currentNode.getElementType() == TokenType.WHITE_SPACE
          || currentNode.getElementType() == JavaTokenType.COMMA
          || StdTokenSets.COMMENT_BIT_SET.contains(currentNode.getElementType())) {
        continue;
      }
      if (currentNode.getElementType() != JavaElementType.FIELD || !compoundFieldPart(currentNode)) {
        break;
      }
      currentResult = currentNode;
    }
    return currentResult;
  }

}
