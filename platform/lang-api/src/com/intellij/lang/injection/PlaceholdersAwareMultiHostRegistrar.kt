// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.util.text.TextRangeUtil
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
class PlaceholdersAwareMultiHostRegistrar(private val multiHostRegistrar: MultiHostRegistrar) : MultiHostRegistrar {

  override fun startInjecting(language: Language): PlaceholdersAwareMultiHostRegistrar {
    multiHostRegistrar.startInjecting(language)
    return this
  }

  override fun startInjecting(language: Language, extension: String?): PlaceholdersAwareMultiHostRegistrar {
    super.startInjecting(language, extension)
    return this
  }

  private var pendingPlaceholder: String? = null

  private var lastRange: TextRange? = null
  private var lastHost: PsiLanguageInjectionHost? = null

  private var postponedAdd: (() -> Unit)? = null

  fun addPlaceholder(text: String) {
    pendingPlaceholder = pendingPlaceholder.orEmpty() + text
  }

  fun addPlace(host: PsiLanguageInjectionHost, rangeInsideHost: TextRange) =
    addPlace(null, null, host, rangeInsideHost)

  override fun addPlace(prefix: String?,
                        suffix: String?,
                        host: PsiLanguageInjectionHost,
                        rangeInsideHost: TextRange): PlaceholdersAwareMultiHostRegistrar {
    if (pendingPlaceholder != null && postponedAdd == null) {
      multiHostRegistrar.addPlace(null, pendingPlaceholder, host, TextRange.from(rangeInsideHost.startOffset, 0))
    }
    postponedAdd?.invoke()
    postponedAdd = fun() {
      val mixedSuffix = if (pendingPlaceholder != null || suffix != null) pendingPlaceholder.orEmpty() + suffix.orEmpty() else null
      pendingPlaceholder = null
      multiHostRegistrar.addPlace(prefix, mixedSuffix, host, rangeInsideHost)
      lastRange = rangeInsideHost
      lastHost = host
    }
    return this
  }

  override fun doneInjecting() {
    val thereWasPlaceholder = pendingPlaceholder != null
    postponedAdd?.invoke()
    if (thereWasPlaceholder) {
      val lastRange = lastRange
      val lastHost = lastHost
      if (lastRange != null && lastHost != null) {
        multiHostRegistrar.addPlace(null, null, lastHost, TextRange.from(lastRange.endOffset, 0))
      }
    }
    multiHostRegistrar.doneInjecting()
  }


}

@ApiStatus.Experimental
class MultiHostRegistrarPlaceholderFacade(private val placeholdersAwareMultiHostRegistrar: PlaceholdersAwareMultiHostRegistrar) {

  constructor(multiHostRegistrar: MultiHostRegistrar) : this(PlaceholdersAwareMultiHostRegistrar(multiHostRegistrar))

  private val globalPlaceholders = mutableListOf<Pair<TextRange, String>>()

  fun addGlobalForeignElements(globalPlaceholders: Iterable<Pair<TextRange, String>>) {
    this.globalPlaceholders.addAll(globalPlaceholders)
  }

  fun addWithPlaceholders(host: PsiLanguageInjectionHost, localForeignElements: Map<TextRange, String>) {
    val valueTextRange = ElementManipulators.getValueTextRange(host)

    val foreignElements = localForeignElements + getGlobalPlaceholdersForHost(host)

    val interpolations = foreignElements.keys.toList()

    val qlRanges = TextRangeUtil.excludeRanges(valueTextRange, interpolations)
    val rangesMap = TreeMap<TextRange, String?>(TextRangeUtil.RANGE_COMPARATOR).apply {
      qlRanges.forEach { this[it] = null }
      this.putAll(foreignElements)
    }

    for ((range, placeHolder) in rangesMap) {
      if (placeHolder != null) {
        placeholdersAwareMultiHostRegistrar.addPlaceholder(placeHolder)
      }
      else {
        placeholdersAwareMultiHostRegistrar.addPlace(host, range)
      }
    }
  }

  private fun getGlobalPlaceholdersForHost(host: PsiLanguageInjectionHost) =
    globalPlaceholders.asSequence().filter { host.textRange.containsOffset(it.first.startOffset) }
      .map { (k, v) -> k.shiftLeft(host.textRange.startOffset) to (v) }


  fun startInjecting(language: Language): MultiHostRegistrarPlaceholderFacade {
    placeholdersAwareMultiHostRegistrar.startInjecting(language)
    return this
  }

  fun startInjecting(language: Language, extension: String?): MultiHostRegistrarPlaceholderFacade {
    placeholdersAwareMultiHostRegistrar.startInjecting(language, extension)
    return this
  }

  fun doneInjecting() {
    placeholdersAwareMultiHostRegistrar.doneInjecting()
  }

}