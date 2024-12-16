// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.AutoCompletionPolicy.NEVER_AUTOCOMPLETE
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
class CommandCompletionLookupElement(
  lookupElement: LookupElement,
  val hostStartOffset: Int,
  val suffix: String,
  val icon: Icon?,
  val highlighting: HighlightInfoLookup?,
) : LookupElementDecorator<LookupElement>(lookupElement) {
  override fun isWorthShowingInAutoPopup(): Boolean {
    return true
  }

  override fun getAutoCompletionPolicy(): AutoCompletionPolicy? {
    return NEVER_AUTOCOMPLETE
  }
}

@ApiStatus.Experimental
data class HighlightInfoLookup(
  val range: TextRange,
  val attributesKey: TextAttributesKey,
  val priority: Int, //higher is on the top
)