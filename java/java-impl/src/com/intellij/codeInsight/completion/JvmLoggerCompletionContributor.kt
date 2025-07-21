// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.module.ModuleUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.util.ProcessingContext
import com.siyeh.ig.psiutils.ExpressionUtils

public class JvmLoggerCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           psiElement().withParent(PsiReferenceExpression::class.java).andNot(
             psiElement().withParent(psiElement(PsiReferenceExpression::class.java).withChild(psiElement(PsiExpression::class.java)))),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val parent = parameters.position.parent ?: return
               if (parent !is PsiReferenceExpression ||
                   !ExpressionUtils.isVoidContext(parent) ||
                   JavaKeywordCompletion.isInstanceofPlace(parameters.position)) return
               val javaResultWithSorting = JavaCompletionSorting.addJavaSorting(parameters, result)
               val module = ModuleUtil.findModuleForFile(parameters.originalFile) ?: return
               val availableLoggers = JvmLogger.findSuitableLoggers(module, true)

               val element = parameters.originalPosition ?: return
               val allPlaces = JvmLogger.getAllNamedContainingClasses(element)
               val possiblePlaces = JvmLogger.getPossiblePlacesForLogger(element, availableLoggers)
               if (allPlaces.size != possiblePlaces.size) return

               val place = possiblePlaces.firstOrNull() ?: return

               for (logger in availableLoggers) {
                 val lookupElement = JvmLoggerLookupElement(logger, place)
                 javaResultWithSorting.addElement(lookupElement)
               }
             }
           })
  }

  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    super.fillCompletionVariants(parameters, result)
  }
}
