// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.module.ModuleUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

class JvmLoggerCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           StandardPatterns.or(
           psiElement()
             .withSuperParent(2, PsiExpressionStatement::class.java)
             .afterLeaf(StandardPatterns.or(
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.SEMICOLON),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.COLON),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.LBRACE),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.RBRACE),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.ARROW),
               psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.RPARENTH).and(
                 StandardPatterns.or(
                 psiElement().withParent(PsiIfStatement::class.java),
                 psiElement().withParent(PsiForStatement::class.java),
                 psiElement().withParent(PsiForeachStatement::class.java),
                 psiElement().withParent(PsiWhileStatement::class.java)
                 )
               )
             )),
           psiElement().withSuperParent(2, PsiLambdaExpression::class.java).afterLeaf(
             psiElement(PsiJavaToken::class.java).withElementType(JavaTokenType.ARROW)
           )
           ),
           object : CompletionProvider<CompletionParameters>() {
             override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
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
}
