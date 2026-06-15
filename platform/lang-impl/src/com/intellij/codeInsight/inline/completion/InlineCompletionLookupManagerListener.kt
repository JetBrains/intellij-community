// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace

private class InlineCompletionLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    LOG.trace {
      "[Inline Completion] activeLookupChanged (clientId=${ClientId.currentOrNull}): " +
      "old=${oldLookup?.let { "${it.javaClass.simpleName}@${System.identityHashCode(it)}" }}, " +
      "new=${newLookup?.let { "${it.javaClass.simpleName}@${System.identityHashCode(it)}" }}"
    }
    newLookup?.addLookupListener(object : LookupListener {
      override fun currentItemChanged(event: LookupEvent) {
        if (event.item == null) {
          LOG.trace { "[Inline Completion] currentItemChanged ignored: item is null (clientId=${ClientId.currentOrNull})" }
          return
        }
        val editor = runReadActionBlocking { event.lookup.editor }
        val lookupChanged = InlineCompletionEvent.LookupChange(editor, event)
        val handler = InlineCompletion.getHandlerOrNull(lookupChanged.topLevelEditor)
        LOG.trace {
          "[Inline Completion] currentItemChanged (clientId=${ClientId.currentOrNull}): " +
          "item='${event.item?.lookupString}', handler=${if (handler != null) "present" else "null"}"
        }
        if (handler == null) return
        handler.invokeEvent(InlineCompletionEvent.LookupChange(editor, event))
      }

      override fun lookupCanceled(event: LookupEvent) {
        val editor = runReadActionBlocking { event.lookup.editor }
        val lookupCancelled = InlineCompletionEvent.LookupCancelled(editor, event)
        val handler = InlineCompletion.getHandlerOrNull(lookupCancelled.topLevelEditor)
        LOG.trace {
          "[Inline Completion] lookupCanceled (clientId=${ClientId.currentOrNull}): " +
          "handler=${if (handler != null) "present" else "null"}"
        }
        if (handler == null) return
        handler.invokeEvent(lookupCancelled)
      }
    })
  }

  companion object {
    private val LOG = logger<InlineCompletionLookupManagerListener>()
  }
}
