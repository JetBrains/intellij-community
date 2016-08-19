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

import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/**
 * @author Denis Zhdanov
 * @since 4/12/11 3:26 PM
 */
public class JavaFormatterUtil {
  /**
   * Holds type of AST elements that are considered to be assignments.
   */
  private static final Set<IElementType> ASSIGNMENT_ELEMENT_TYPES = new HashSet<>(asList(
    JavaElementType.ASSIGNMENT_EXPRESSION, JavaElementType.LOCAL_VARIABLE, JavaElementType.FIELD
  ));

  private JavaFormatterUtil() { }

  /**
   * Allows to answer if given node wraps assignment operation.
   *
   * @param node node to check
   * @return <code>true</code> if given node wraps assignment operation; <code>false</code> otherwise
   */
  public static boolean isAssignment(ASTNode node) {
    return ASSIGNMENT_ELEMENT_TYPES.contains(node.getElementType());
  }

  /**
   * Allows to check if given <code>AST</code> nodes refer to binary expressions which have the same priority.
   *
   * @param node1 node to check
   * @param node2 node to check
   * @return <code>true</code> if given nodes are binary expressions and have the same priority;
   *         <code>false</code> otherwise
   */
  public static boolean areSamePriorityBinaryExpressions(ASTNode node1, ASTNode node2) {
    if (node1 == null || node2 == null) {
      return false;
    }

    if (!(node1 instanceof PsiPolyadicExpression) || !(node2 instanceof PsiPolyadicExpression)) {
      return false;
    }
    PsiPolyadicExpression expression1 = (PsiPolyadicExpression)node1;
    PsiPolyadicExpression expression2 = (PsiPolyadicExpression)node2;
    return expression1.getOperationTokenType() == expression2.getOperationTokenType();
  }

  public static boolean hasMultilineArguments(@NotNull PsiExpression[] arguments) {
    for (PsiExpression argument: arguments) {
      ASTNode node = argument.getNode();
      if (node.textContains('\n'))
        return true;
    }

    return false;
  }

  public static boolean isMultilineExceptArguments(@NotNull PsiExpression[] arguments) {
    for (PsiExpression argument : arguments) {
      ASTNode beforeArgument = argument.getNode().getTreePrev();
      if (isWhiteSpaceWithLineFeed(beforeArgument))
        return true;
    }

    PsiExpression lastArgument = arguments[arguments.length - 1];
    ASTNode afterLastArgument = lastArgument.getNode().getTreeNext();
    return isWhiteSpaceWithLineFeed(afterLastArgument);
  }

  private static boolean isWhiteSpaceWithLineFeed(@NotNull ASTNode node) {
    return node instanceof PsiWhiteSpace
           && node.textContains('\n');
  }

  @NotNull
  public static WrapType getWrapType(int wrap) {
    switch (wrap) {
      case CommonCodeStyleSettings.WRAP_ALWAYS:
        return WrapType.ALWAYS;
      case CommonCodeStyleSettings.WRAP_AS_NEEDED:
        return WrapType.NORMAL;
      case CommonCodeStyleSettings.DO_NOT_WRAP:
        return WrapType.NONE;
      default:
        return WrapType.CHOP_DOWN_IF_LONG;
    }
  }
}
