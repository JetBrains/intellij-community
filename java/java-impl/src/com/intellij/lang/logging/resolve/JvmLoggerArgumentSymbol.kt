// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.logging.resolve

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.*

class JvmLoggerArgumentSymbol(val expression: PsiExpression) : Symbol, NavigatableSymbol, SearchTarget {
  override val usageHandler: UsageHandler = UsageHandler.createEmptyUsageHandler(expression.text)

  override fun createPointer(): Pointer<JvmLoggerArgumentSymbol> {
    return Pointer.delegatingPointer(SmartPointerManager.createPointer(expression), ::JvmLoggerArgumentSymbol)
  }

  fun getFormatString(): PsiLiteralExpression? {
    val expressionList = expression.parent as? PsiExpressionList ?: return null
    if (expressionList.parent !is PsiCallExpression) return null
    return expressionList.expressions[0] as? PsiLiteralExpression
  }

  override fun presentation(): TargetPresentation = TargetPresentation.builder(expression.text).presentation()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(expression))

  override fun equals(other: Any?): Boolean {
    if (other !is JvmLoggerArgumentSymbol) return false
    return other.expression == this.expression
  }

  override fun hashCode(): Int = expression.hashCode()
}