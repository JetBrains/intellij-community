// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.psi.util.PsiVersioningService
import com.intellij.util.application

private class InlineCompletionLookupManagerListener : LookupManagerListener {
  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    LOG.trace {
      "[Inline Completion] activeLookupChanged (clientId=${ClientId.currentOrNull}): " +
      "old=${oldLookup?.let { "${it.javaClass.simpleName}@${System.identityHashCode(it)}" }}, " +
      "new=${newLookup?.let { "${it.javaClass.simpleName}@${System.identityHashCode(it)}" }}"
    }
    newLookup?.addLookupListener(object : LookupListener {
      private var pendingChange: LookupEvent? = null

      override fun currentItemChanged(event: LookupEvent) {
        pendingChange = null
        if (event.item == null) {
          LOG.trace { "[Inline Completion] currentItemChanged ignored: item is null (clientId=${ClientId.currentOrNull})" }
          return
        }
        if (application.isReadAccessAllowed) {
          processCurrentItemChanged(event)
          return
        }
        val editor = event.lookup.editor
        val topLevelEditor = InlineCompletionEvent.LookupChange(editor, event).topLevelEditor
        val session = InlineCompletionSession.getOrNull(topLevelEditor)
        pendingChange = event
        application.invokeLater {
          if (pendingChange !== event) return@invokeLater
          pendingChange = null
          val lookup = event.lookup
          if (editor.isDisposed || lookup.project.isDisposed || LookupManager.getActiveLookup(editor) !== lookup) return@invokeLater
          if (lookup.currentItem !== event.item) return@invokeLater
          if (InlineCompletionSession.getOrNull(topLevelEditor) !== session) return@invokeLater
          processCurrentItemChanged(event)
        }
      }

      private fun processCurrentItemChanged(event: LookupEvent) {
        val editor = event.lookup.editor
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
        pendingChange = null
        val editor = PsiVersioningService.freezePsiVersion { event.lookup.editor }
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
