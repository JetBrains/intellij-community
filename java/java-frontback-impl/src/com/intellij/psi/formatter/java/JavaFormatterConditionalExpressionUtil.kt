// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.PsiTreeUtil

internal object JavaFormatterConditionalExpressionUtil {

  /**
   * Checks if the given AST node is inside a conditional expression then or else branch.
   */
  @JvmStatic
  fun isInsideConditionalExpression(node: ASTNode): Boolean {
    val psi = SourceTreeToPsiMap.treeElementToPsi(node)
    val child = PsiTreeUtil.findFirstParent(psi) {
      it.parent is PsiConditionalExpression ||
      it.parent is PsiMethod ||
      it.parent is PsiLocalVariable
    }
    if (child == null) return false
    val parent = child.parent
    if (parent !is PsiConditionalExpression) return false
    if (parent !is CompositeElement) return false
    return parent.condition != child
  }
}