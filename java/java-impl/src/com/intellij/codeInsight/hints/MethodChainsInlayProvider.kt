// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.chain.AbstractCallChainHintsProvider
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.siyeh.ig.psiutils.ExpressionUtils

class MethodChainsInlayProvider : AbstractCallChainHintsProvider<PsiMethodCallExpression, PsiType, Unit>() {

  override val group: InlayGroup
    get() = InlayGroup.TYPES_GROUP

  override fun getProperty(key: String): String {
    return JavaBundle.message(key)
  }

  override fun PsiType.getInlayPresentation(
    expression: PsiElement,
    factory: PresentationFactory,
    project: Project,
    context: Unit
  ): InlayPresentation {
    val presentation = JavaTypeHintsPresentationFactory(factory, 3).typeHint(this)
    return InsetPresentation(MenuOnClickPresentation(presentation, project) {
      val provider = this@MethodChainsInlayProvider
      listOf(InlayProviderDisablingAction(provider.name, JavaLanguage.INSTANCE, project, provider.key))
    }, left = 1)
  }

  override fun PsiElement.getType(context: Unit): PsiType? {
    return (this as? PsiExpression)?.type
  }

  override val dotQualifiedClass: Class<PsiMethodCallExpression>
    get() = PsiMethodCallExpression::class.java

  override fun getTypeComputationContext(topmostDotQualifiedExpression: PsiMethodCallExpression) {
    // Java implementation doesn't use any additional type computation context
  }

  override val previewText: String
    get() = """
      abstract class Foo<T> {
          void main() {
              listOf(1, 2, 3).filter(it -> it % 2 == 0)
                      .map(it -> it * 2)
                      .map(it -> "item: " + it)
                      .forEach(this::println);
          }

          abstract Void println(Object any);
          abstract Foo<Integer> listOf(int... args);
          abstract Foo<T> filter(Function<T, Boolean> isAccepted);
          abstract <R> Foo<R> map(Function<T, R> mapper);
          abstract void forEach(Function<T, Void> fun);
          interface Function<T, R> {
              R call(T t);
          }
      }
    """.trimIndent()

  override fun PsiMethodCallExpression.getReceiver(): PsiElement? {
    return methodExpression.qualifier
  }

  override fun PsiMethodCallExpression.getParentDotQualifiedExpression(): PsiMethodCallExpression? {
    return ExpressionUtils.getCallForQualifier(this)
  }

  override fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement? {
    var expr: PsiElement? = this
    while (true) {
      expr = if (expr is PsiParenthesizedExpression) expr.expression else break
    }
    return expr as? PsiMethodCallExpression
  }

  override val key: SettingsKey<Settings> = SettingsKey("chain.hints")
}
