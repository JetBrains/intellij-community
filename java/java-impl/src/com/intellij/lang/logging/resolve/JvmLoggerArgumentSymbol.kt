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
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.toUElementOfType

class JvmLoggerArgumentSymbol(val expression: PsiElement) : Symbol, NavigatableSymbol, SearchTarget {
  override val usageHandler: UsageHandler = UsageHandler.createEmptyUsageHandler(expression.text)


  fun getPlaceholderString() : UExpression? {
    val uExpression = expression.toUElementOfType<UExpression>() ?: return null
    val uCallExpression = uExpression.getParentOfType<UCallExpression>() ?: return null
    val searcher = LOGGER_RESOLVE_TYPE_SEARCHERS.mapFirst(uCallExpression) ?: return null

    val arguments = uCallExpression.valueArguments
    if (arguments.isEmpty() && searcher != SLF4J_BUILDER_HOLDER) return null

    val loggerType = searcher.findType(uCallExpression, LoggerContext(false)) ?: return null

    return getPlaceholderContext(uCallExpression, searcher, loggerType)?.logStringArgument
  }

  override fun createPointer(): Pointer<JvmLoggerArgumentSymbol> {
    return Pointer.delegatingPointer(SmartPointerManager.createPointer(expression), ::JvmLoggerArgumentSymbol)
  }

  override fun presentation(): TargetPresentation = TargetPresentation.builder(expression.text).presentation()

  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = listOf(SymbolNavigationService.getInstance().psiElementNavigationTarget(expression))

  override fun equals(other: Any?): Boolean {
    if (other !is JvmLoggerArgumentSymbol) return false
    return other.expression == this.expression
  }

  override fun hashCode(): Int = expression.hashCode()
}