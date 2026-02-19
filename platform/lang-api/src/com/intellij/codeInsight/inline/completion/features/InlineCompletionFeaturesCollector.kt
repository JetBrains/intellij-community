// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.features

import com.intellij.codeInsight.inline.completion.features.InlineCompletionFeaturesScopeAnalyzer.ScopeType
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionFeaturesCollector {
  fun getAllImports(file: PsiFile): Collection<PsiElement>
  fun getReceiverClassElement(element: PsiElement): PsiElement?
  fun countLibraries(project: Project, imports: Collection<PsiElement>): Int?
  fun countPopularLibraries(sources: Collection<String>): Int
  fun classifyByImportsToTopics(sources: Collection<String>): Map<String, Boolean>
  fun getSourceNames(imports: Collection<PsiElement>): Collection<String>
  fun isInAbstractMethodBody(element: PsiElement): Boolean?
  fun getNumOfPrevQualifiers(element: PsiElement): Int?
  fun getPrevNeighboursKeywordIds(element: PsiElement): List<Int>
  fun getPrevKeywordsIdsInTheSameLine(element: PsiElement): List<Int>
  fun getPrevKeywordsIdsInTheSameColumn(element: PsiElement): List<Int>
  fun getArgumentFeatures(element: PsiElement): ArgumentFeatures?
  fun getBracketFeatures(element: PsiElement): BracketFeatures?
  fun isInConditionalStatement(element: PsiElement): Boolean?
  fun isInForStatement(element: PsiElement): Boolean?
  fun getBlockStatementLevel(element: PsiElement): Int
  fun getSuggestionReferenceFeatures(fileWithSuggestion: PsiFile, suggestionRange: TextRange): List<SuggestionReferenceFeatures>
  fun getExtendedScopeFeatures(file: PsiFile, offset: Int): ExtendedScopeFeatures

  data class ArgumentFeatures(
    val isInArguments: Boolean,
    val isDirectlyInArgumentContext: Boolean,
    val argumentIndex: Int?,
    val argumentsSize: Int?,
    val isIntoKeywordArgument: Boolean? = null,
    val haveNamedArgLeft: Boolean? = null,
    val haveNamedArgRight: Boolean? = null,
  )

  data class BracketFeatures(
    val haveOpeningParenthesisOnTheLeft: Boolean,
    val haveOpeningBracketOnTheLeft: Boolean,
    val haveOpeningBraceOnTheLeft: Boolean,
    val haveOpeningAngleBracketOnTheLeft: Boolean,
  )

  data class SuggestionReferenceFeatures(
    val fromLibrary: Boolean,
    val inClass: Boolean,
    val inFunction: Boolean,
    val inSameFile: Boolean,
    val inSameClass: Boolean,
    val inSameFunction: Boolean,
    val isFunction: Boolean,
    val isClass: Boolean,
  )

  data class ExtendedScopeFeatures(
    val scopeFeatures: ScopeFeatures?,
    val parentScopeFeatures: ScopeFeatures?,
    val grandParentScopeFeatures: ScopeFeatures?,
    val prevSiblingScopeFeatures: ScopeFeatures?,
    val nextSiblingScopeFeatures: ScopeFeatures?,
  )

  data class ScopeFeatures(
    val type: ScopeType,
    val isInit: Boolean,
    val numLines: Int,
    val numSymbols: Int,
    val lineOffset: Int,
    val offset: Int, // caret after offset symbol
    val valuableSymbolsBefore: Boolean?,
    val valuableSymbolsAfter: Boolean?,
    val hasErrorPsi: Boolean?,
  )

  companion object {
    private val EP_NAME = LanguageExtension<InlineCompletionFeaturesCollector>("com.intellij.mlCompletionFeaturesCollector")
    fun get(language: Language): InlineCompletionFeaturesCollector? = EP_NAME.forLanguage(language)
  }
}
