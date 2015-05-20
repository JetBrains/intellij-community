/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaJspElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import org.jetbrains.annotations.NotNull;

public class MultipleFieldDeclarationHelper {

  /**
   * @return <code>true</code> if given node is a non-first part of composite field definition; <code>false</code> otherwise
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
   * <code>'Composite field definition'</code> looks like {@code 'int i1, i2 = 2'}. It produces two nodes of type
   * {@link JavaElementType#FIELD} - {@code 'int i1'} and {@code 'i2 = 2'}. This method returns the second node if the first one
   * is given (the given node is returned for <code>'single'</code> fields).
   *
   * @param child     child field node to check
   * @return          last child field node at the field group identified by the given node if any; given child otherwise
   */
  @NotNull
  public static ASTNode findLastFieldInGroup(@NotNull final ASTNode child) {
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
    ASTNode currentNode = child.getTreeNext();

    while (currentNode != null) {
      if (currentNode.getElementType() == TokenType.WHITE_SPACE
          || currentNode.getElementType() == JavaTokenType.COMMA
          || StdTokenSets.COMMENT_BIT_SET.contains(currentNode.getElementType())) {
      }
      else if (currentNode.getElementType() == JavaElementType.FIELD) {
        if (compoundFieldPart(currentNode)) {
          currentResult = currentNode;
        }
        else {
          return currentResult;
        }
      }
      else {
        return currentResult;
      }

      currentNode = currentNode.getTreeNext();
    }
    return currentResult;
  }

}
