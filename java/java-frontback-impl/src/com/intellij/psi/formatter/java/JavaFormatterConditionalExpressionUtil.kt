// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil
import com.intellij.psi.impl.source.tree.ChildRole
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.tree.ParentAwareTokenSet

internal object JavaFormatterConditionalExpressionUtil {
  private val STOP_TOKENS = ParentAwareTokenSet.create(JavaElementType.METHOD, JavaElementType.LOCAL_VARIABLE, JavaElementType.METHOD)

  /**
   * Checks if the given AST node is inside a conditional expression then or else branch.
   */
  @JvmStatic
  fun isInsideConditionalExpression(node : ASTNode) : Boolean {
    val child = BasicJavaAstTreeUtil.getAncestorWithParentOfType(node, JavaElementType.CONDITIONAL_EXPRESSION, STOP_TOKENS)
    if (child == null) return false
    val parent = child.treeParent
    if (parent !is CompositeElement) return false
    val childRole = parent.getChildRole(child)
    return childRole != ChildRole.CONDITION
  }
}