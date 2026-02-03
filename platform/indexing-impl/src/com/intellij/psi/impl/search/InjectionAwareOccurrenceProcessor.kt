// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.search.LeafOccurrence
import com.intellij.model.search.impl.LanguageInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import com.intellij.util.text.StringSearcher

internal class InjectionAwareOccurrenceProcessor(
  private val progress: ProgressIndicator,
  private val processors: RequestProcessors,
  private val manager: InjectedLanguageManager,
  private val searcher: StringSearcher
) : OccurrenceProcessor {

  private val processedInjections = HashSet<PsiElement>()

  override fun invoke(occurrence: LeafOccurrence): Boolean {
    if (processors.injectionProcessors.isEmpty()) {
      return processHost(occurrence)
    }
    // TODO find injections of needed languages only, needed languages are the key set of languageInjectionProcessors map
    val injected: PsiElement? = findInjectionAtOffset(occurrence)
    if (injected == null) {
      return processHost(occurrence)
    }
    if (!processedInjections.add(injected)) {
      // The same injection might contain several occurrences at different offsets, we don't know about them beforehand.
      // Since `LowLevelSearchUtil.getTextOccurrencesInScope` is used
      // its possible to end up in the situation when N occurrences in a single injection are processed N times each.
      return true
    }
    val injectionProcessors: Collection<OccurrenceProcessor> = getLanguageProcessors(injected.language)
    if (injectionProcessors.isEmpty()) {
      return true
    }
    val offsets = LowLevelSearchUtil.getTextOccurrencesInScope(injected, searcher)
    val patternLength = searcher.patternLength
    return processOffsets(injected, offsets, patternLength, progress, injectionProcessors.compound(progress))
  }

  private fun getLanguageProcessors(injectionLanguage: Language): Collection<OccurrenceProcessor> {
    val injectionProcessors = processors.injectionProcessors
    val result = SmartList<OccurrenceProcessor>()
    for ((languageInfo, processors) in injectionProcessors) {
      if (languageInfo == LanguageInfo.NoLanguage ||
          languageInfo is LanguageInfo.InLanguage && languageInfo.matcher.matchesLanguage(injectionLanguage)) {
        result += processors
      }
    }
    return result
  }

  private fun findInjectionAtOffset(occurrence: LeafOccurrence): PsiElement? {
    for ((currentElement, currentOffset) in occurrence.elementsUp()) {
      progress.checkCanceled()
      val injectedFiles: List<Pair<PsiElement, TextRange>> = manager.getInjectedPsiFiles(currentElement) ?: continue

      for ((injected, range) in injectedFiles) {
        if (range.containsRange(currentOffset, currentOffset + searcher.patternLength)) {
          return injected
        }
      }
    }
    return null
  }

  private fun processHost(occurrence: LeafOccurrence): Boolean {
    // Feed host element into the processors which doesn't want injections.
    return processors.hostProcessors.runProcessors(progress, occurrence)
  }
}
