// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.LookupElement

public class LoggerWeigher : CompletionWeigher() {
  override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<*> = when (element) {
    is JvmLoggerLookupElement -> -1
    else -> 1
  }
}