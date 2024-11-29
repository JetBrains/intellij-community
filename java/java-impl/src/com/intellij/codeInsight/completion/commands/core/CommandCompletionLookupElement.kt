// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.AutoCompletionPolicy.NEVER_AUTOCOMPLETE
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.modcommand.ModHighlight.HighlightInfo
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
//todo customize
class CommandCompletionLookupElement(
  lookupElement: LookupElement,
  val startOffset: Int,
  val suffix: String,
  val icon: Icon?,
  val highlighting: HighlightInfo?
) : LookupElementDecorator<LookupElement>(lookupElement) {
  override fun isWorthShowingInAutoPopup(): Boolean {
    return true
  }

  override fun getAutoCompletionPolicy(): AutoCompletionPolicy? {
    return NEVER_AUTOCOMPLETE
  }
}