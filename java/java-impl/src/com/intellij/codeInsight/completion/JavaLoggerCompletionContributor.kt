// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.generation.GenerateLoggerHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.openapi.module.ModuleUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiCodeBlock
import com.intellij.util.ProcessingContext

class JavaLoggerCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           psiElement().inside(psiElement(PsiCodeBlock::class.java)),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
               val module = ModuleUtil.findModuleForFile(parameters.originalFile)
               val availableLoggers = GenerateLoggerHandler.findSuitableLoggers(module)

               val element = parameters.originalPosition ?: return
               val place = GenerateLoggerHandler.getPossiblePlacesForLogger(element, availableLoggers).firstOrNull() ?: return

               for (logger in availableLoggers) {
                 result.addElement(
                   LookupElementBuilder
                     .create(JvmLoggerFieldDelegate.LOGGER_IDENTIFIER)
                     .withTailText(" ${logger.loggerTypeName}")
                     .withTypeText(logger.toString())
                     .withInsertHandler { insertionContext, _ ->
                       val loggerText = logger.createLogger(insertionContext.project, place) ?: return@withInsertHandler
                       logger.insertLoggerAtClass(insertionContext.project, place, loggerText)
                     }
                 )
               }
             }
           })
  }
}