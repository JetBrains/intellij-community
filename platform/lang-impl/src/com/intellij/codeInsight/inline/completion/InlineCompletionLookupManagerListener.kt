// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener

class InlineCompletionLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    newLookup?.addLookupListener(object : LookupListener {
      override fun currentItemChanged(event: LookupEvent) {
        if (event.item == null) return
        val listener = InlineCompletion.getHandlerOrNull(event.lookup.editor) ?: return
        listener.invoke(InlineCompletionEvent.LookupChange(event))
      }

      override fun lookupCanceled(event: LookupEvent) {
        val listener = InlineCompletion.getHandlerOrNull(event.lookup.editor) ?: return
        listener.invoke(InlineCompletionEvent.LookupCancelled(event))
      }
    })
  }
}
