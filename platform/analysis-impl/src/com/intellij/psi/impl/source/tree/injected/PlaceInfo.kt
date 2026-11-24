// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost

/**
 * Stores injection registration info temporarily
 * from the [MultiHostRegistrar.startInjecting] call
 * to the moment of [MultiHostRegistrar.doneInjecting].
 */
internal class PlaceInfo(
  val prefix: String,
  val suffix: String,
  val host: PsiLanguageInjectionHost,
  val registeredRangeInsideHost: TextRange,
  val hostText: String,
) {
  val escaper: LiteralTextEscaper<out PsiLanguageInjectionHost> = host.createLiteralTextEscaper()
  var rangeInDecodedPSI: ProperTextRange? = null
  var rangeInHostElement: TextRange? = null
  var newInjectionHostRange: Segment? = null

  override fun toString(): String {
    return "Shred " +
           (if (prefix.isEmpty()) "" else "prefix='$prefix' ") +
           (if (suffix.isEmpty()) "" else "suffix='$suffix' ") +
           "in $host with range ${host.textRange} (${host.javaClass}) " +
           "inside range $registeredRangeInsideHost"
  }

  fun getRelevantRangeInsideHost(): TextRange? = escaper.relevantTextRange.intersection(registeredRangeInsideHost)
}
