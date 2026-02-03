// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupActionProvider
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementAction
import com.intellij.util.Consumer

public class ExcludeLoggerFromCompletionLookupActionProvider : LookupActionProvider {
  override fun fillActions(lookupElement: LookupElement, lookup: Lookup, consumer: Consumer<in LookupElementAction>) {
    if (lookupElement is JvmLoggerLookupElement) {
      for (s in AddImportAction.getAllExcludableStrings(lookupElement.typeName)) {
        consumer.consume(ExcludeFromCompletionAction(lookup.project, s))
      }
    }
  }
}