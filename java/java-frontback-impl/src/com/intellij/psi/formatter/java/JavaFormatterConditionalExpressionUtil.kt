// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.ChildRole
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.psi.tree.TokenSet

internal object JavaFormatterConditionalExpressionUtil {
  private val STOP_TYPES: TokenSet = TokenSet.create(
    JavaElementType.METHOD,
    JavaElementType.ANNOTATION_METHOD,
    JavaElementType.LOCAL_VARIABLE,
    JavaElementType.RESOURCE_VARIABLE,
  )

  private val BINARY_EXPRESSION_TYPES: TokenSet = TokenSet.create(
    JavaElementType.POLYADIC_EXPRESSION,
    JavaElementType.BINARY_EXPRESSION,
  )

  /**
   * Checks if the given AST node is inside a conditional expression then or else branch.
   */
  @JvmStatic
  fun isInsideConditionalExpression(node: ASTNode): Boolean {
    var child: ASTNode = node
    var parent: ASTNode? = child.treeParent
    while (parent != null) {
      val parentType = parent.elementType
      if (parentType == JavaElementType.CONDITIONAL_EXPRESSION) {
        if (parent !is CompositeElement) return false
        return parent.getChildRole(child) != ChildRole.CONDITION
      }
      if (STOP_TYPES.contains(parentType)) return false
      child = parent
      parent = child.treeParent
    }
    return false
  }

  /**
   * Checks if the given AST node is inside a binary expression, with the search scope limited to the conditional expression.
   */
  @JvmStatic
  fun isInsideBinaryExpression(node: ASTNode): Boolean {
    var parent: ASTNode? = node.treeParent
    while (parent != null) {
      val parentType = parent.elementType
      if (BINARY_EXPRESSION_TYPES.contains(parentType)) return true
      if (parentType == JavaElementType.CONDITIONAL_EXPRESSION) return false
      parent = parent.treeParent
    }
    return false
  }
}