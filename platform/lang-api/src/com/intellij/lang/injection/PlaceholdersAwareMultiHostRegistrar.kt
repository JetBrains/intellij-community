// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.injection

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiLanguageInjectionHost
import org.jetbrains.annotations.ApiStatus

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