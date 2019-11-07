// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.function.Function


class MultiHostRegistrarPlaceholderHelper(private val multiHostRegistrar: MultiHostRegistrar) {

  private val globalPlaceholders = mutableListOf<Pair<TextRange, String>>()

  fun addGlobalPlaceholders(globalPlaceholders: Iterable<Pair<TextRange, String>>): MultiHostRegistrarPlaceholderHelper {
    this.globalPlaceholders.addAll(globalPlaceholders)
    return this
  }

  private var lastPlaceholderEnd: Int = -1
  private var lastHost: PsiLanguageInjectionHost? = null

  fun addHostPlaces(host: PsiLanguageInjectionHost, hostPlaceholders: List<Pair<TextRange, String>>): MultiHostRegistrarPlaceholderHelper {
    val valueTextRange = ElementManipulators.getValueTextRange(host)

    val foreignElements = hostPlaceholders + getGlobalPlaceholdersForHost(host)
    val interpolations = foreignElements.map { it.first }
    val textRanges = TextRangeUtil.excludeRanges(valueTextRange, interpolations).map { it to null }

    val ranges = (textRanges + foreignElements)
      .sortedWith(Comparator.comparing(Function(Pair<TextRange, String?>::first), TextRangeUtil.RANGE_COMPARATOR))

    lastHost = host
    lastPlaceholderEnd = addRangesToPlaces(host, ranges)
    return this
  }

  private tailrec fun addRangesToPlaces(host: PsiLanguageInjectionHost, ranges: List<Pair<TextRange, String?>>): Int {
    if (ranges.isEmpty()) return -1

    val (textRange, placeholder) = ranges.first()
    val (placeholders, remaining) = ranges.subList(1, ranges.size).splitByPredicate { (_, placeHolder) -> placeHolder != null }
    val joinedPlaceholders = placeholder.orEmpty() + placeholders.joinToString("") { it.second!! }

    val rangeForPlace = if (placeholder != null)
      TextRange.from(remaining.firstOrNull()?.first?.startOffset ?: textRange.endOffset, 0)
    else
      textRange

    multiHostRegistrar.addPlace(null, joinedPlaceholders, host, rangeForPlace)

    if (remaining.isEmpty()) {
      return if (joinedPlaceholders.isNotEmpty()) textRange.endOffset else -1
    }
    return addRangesToPlaces(host, remaining)
  }


  private fun getGlobalPlaceholdersForHost(host: PsiLanguageInjectionHost) =
    globalPlaceholders.asSequence().filter { host.textRange.containsOffset(it.first.startOffset) }
      .map { (k, v) -> k.shiftLeft(host.textRange.startOffset) to (v) }


  fun startInjecting(language: Language): MultiHostRegistrarPlaceholderHelper {
    multiHostRegistrar.startInjecting(language)
    return this
  }

  fun startInjecting(language: Language, extension: String?): MultiHostRegistrarPlaceholderHelper {
    multiHostRegistrar.startInjecting(language, extension)
    return this
  }

  fun doneInjecting() {
    if (lastPlaceholderEnd != -1)
      multiHostRegistrar.addPlace(null, null, lastHost!!, TextRange.from(lastPlaceholderEnd, 0))

    multiHostRegistrar.doneInjecting()
  }

}

private fun <T> List<T>.splitByPredicate(predicate: (T) -> Boolean): Pair<List<T>, List<T>> {
  var splitPosition = 0
  for (elem in this) {
    if (predicate(elem)) splitPosition++
    else break
  }
  return this.subList(0, splitPosition) to this.subList(splitPosition, this.size)
}