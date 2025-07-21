// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.chain.AbstractDeclarativeCallChainProvider
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.siyeh.ig.psiutils.ExpressionUtils

public class JavaMethodChainsDeclarativeInlayProvider : AbstractDeclarativeCallChainProvider<PsiMethodCallExpression, PsiType, Unit>() {
  public companion object {
    public const val PROVIDER_ID: String = "java.method.chains"
  }

  override fun PsiType.buildTree(expression: PsiElement, project: Project, context: Unit, treeBuilder: PresentationTreeBuilder) {
    JavaTypeHintsFactory.typeHint(this, treeBuilder)
  }

  override fun PsiElement.getType(context: Unit): PsiType? {
    return (this as? PsiExpression)?.type
  }

  override val dotQualifiedClass: Class<PsiMethodCallExpression>
    get() = PsiMethodCallExpression::class.java

  override fun getTypeComputationContext(topmostDotQualifiedExpression: PsiMethodCallExpression) {
    // Java implementation doesn't use any additional type computation context
  }

  override fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement? {
    var expr: PsiElement? = this
    while (true) {
      expr = if (expr is PsiParenthesizedExpression) expr.expression else break
    }
    return expr as? PsiMethodCallExpression
  }

  override fun PsiMethodCallExpression.getReceiver(): PsiElement? {
    return methodExpression.qualifier
  }

  override fun PsiMethodCallExpression.getParentDotQualifiedExpression(): PsiMethodCallExpression? {
    return ExpressionUtils.getCallForQualifier(this)
  }

  override fun presentableType(type: PsiType): String {
    return type.presentableText
  }
}