// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.application.runReadAction

private class InlineCompletionLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    newLookup?.addLookupListener(object : LookupListener {
      override fun currentItemChanged(event: LookupEvent) {
        if (event.item == null) return
        val editor = runReadAction { event.lookup.editor }
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
        handler.invokeEvent(InlineCompletionEvent.LookupChange(editor, event))
      }

      override fun lookupCanceled(event: LookupEvent) {
        val editor = runReadAction { event.lookup.editor }
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
        handler.invokeEvent(InlineCompletionEvent.LookupCancelled(editor, event))
      }
    })
  }
}
