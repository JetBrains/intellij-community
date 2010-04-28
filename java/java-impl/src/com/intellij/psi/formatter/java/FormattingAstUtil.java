/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * Contains various utility methods for <code>AST</code> processing during code formatting.
 *
 * @author Denis Zhdanov
 * @since Apr 21, 2010 4:02:17 PM
 */
public class FormattingAstUtil {

  /** Holds type of AST elements that are considered to be assignments. */
  private static final Set<IElementType> ASSIGNMENT_ELEMENT_TYPES = new HashSet<IElementType>(asList(
    JavaElementType.ASSIGNMENT_EXPRESSION, JavaElementType.LOCAL_VARIABLE, JavaElementType.FIELD
  ));

  private FormattingAstUtil() {
  }

  /**
   * Tries to get previous non-white space <code>AST</code> node for the given one.
   *
   * @param node    base node which left non-white space sibling is to be found
   * @return        left non-white space sibling of the given node if any; <code>null</code> otherwise
   */
  @Nullable
  public static ASTNode getPrevNonWhiteSpaceNode(final ASTNode node) {
    ASTNode result = node.getTreePrev();
    while (result != null && (result.getElementType() == TokenType.WHITE_SPACE || result.getTextLength() == 0)) {
      result = result.getTreePrev();
    }
    return result;
  }

  /**
   * Tries to get next non-white space <code>AST</code> node for the given one.
   *
   * @param node    base node which right non-white space sibling is to be found
   * @return        right non-white space sibling of the given node if any; <code>null</code> otherwise
   */
  @Nullable
  public static ASTNode getNextNonWhiteSpaceNode(final ASTNode node) {
    ASTNode result = node.getTreeNext();
    while (result != null && (result.getElementType() == TokenType.WHITE_SPACE || result.getTextLength() == 0)) {
      result = result.getTreeNext();
    }
    return result;
  }

  /**
   * Allows to answer if given node wraps assignement operation.
   *
   * @param node    node to check
   * @return        <code>true</code> if given node wraps assignement operation; <code>false</code> otherwise
   */
  public static boolean isAssignment(ASTNode node) {
    return ASSIGNMENT_ELEMENT_TYPES.contains(node.getElementType());
  }

  /**
   * Allows to check if given <code>AST</code> nodes refer to binary expressions and have the same priority.
   *
   * @param node1   node to check
   * @param node2   node to check
   * @return        <code>true</code> if given nodes are binary expressions and have the same priority;
   *                <code>false</code> otherwise
   */
  public static boolean binaryExpressionHasTheSamePriority(ASTNode node1, ASTNode node2) {
    if (node1 == null || node2 == null) {
      return false;
    }

    if (node1.getElementType() != JavaElementType.BINARY_EXPRESSION || node2.getElementType() != JavaElementType.BINARY_EXPRESSION) {
      return false;
    }
    PsiBinaryExpression expression1 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(node1);
    PsiBinaryExpression expression2 = (PsiBinaryExpression)SourceTreeToPsiMap.treeElementToPsi(node2);
    return expression1.getOperationSign().getTokenType() == expression2.getOperationSign().getTokenType();
  }
}
