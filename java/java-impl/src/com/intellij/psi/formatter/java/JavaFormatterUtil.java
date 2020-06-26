// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java;

import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Denis Zhdanov
 */
public final class JavaFormatterUtil {
  /**
   * Holds type of AST elements that are considered to be assignments.
   */
  private static final Set<IElementType> ASSIGNMENT_ELEMENT_TYPES = ContainerUtil
    .set(JavaElementType.ASSIGNMENT_EXPRESSION, JavaElementType.LOCAL_VARIABLE, JavaElementType.FIELD);

  private JavaFormatterUtil() { }

  /**
   * Allows to answer if given node wraps assignment operation.
   *
   * @param node node to check
   * @return {@code true} if given node wraps assignment operation; {@code false} otherwise
   */
  public static boolean isAssignment(ASTNode node) {
    return ASSIGNMENT_ELEMENT_TYPES.contains(node.getElementType());
  }

  /**
   * Allows to check if given {@code AST} nodes refer to binary expressions which have the same priority.
   *
   * @param node1 node to check
   * @param node2 node to check
   * @return {@code true} if given nodes are binary expressions and have the same priority;
   *         {@code false} otherwise
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
