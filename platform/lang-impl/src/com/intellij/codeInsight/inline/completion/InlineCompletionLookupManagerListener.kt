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
      /**
       * The selection change that waits for [application] `invokeLater`, or `null`. Only the newest one runs.
       * A queued change becomes obsolete as soon as the next one arrives, because the update renders the
       * selected item and the newest selection wins.
       */
      private var pendingChange: LookupEvent? = null

      /**
       * `LookupImpl` fires this inside `PsiVersioningService.freezePsiVersion`, which forbids a lock. The
       * update can render a variant or call a provider that needs a write-intent lock.
       *
       * When the caller already holds read access, the nested acquisition is re-entrant and the update runs
       * now. Otherwise it waits for `invokeLater`, which runs the runnable under a write-intent read action.
       * A queued update is dropped when the lookup closed or was replaced, when the selection moved on, or
       * when the session was replaced. The session identity check stops an old event from changing a new
       * session.
       */
      override fun currentItemChanged(event: LookupEvent) {
        pendingChange = null
        if (event.item == null) {
          LOG.trace { "[Inline Completion] currentItemChanged ignored: item is null (clientId=${ClientId.currentOrNull})" }
          return
        }
        val lookupChanged = InlineCompletionEvent.LookupChange(event.lookup.editor, event)
        if (application.isReadAccessAllowed) {
          processCurrentItemChanged(lookupChanged)
          return
        }
        val editor = lookupChanged.editor
        val topLevelEditor = lookupChanged.topLevelEditor
        val session = InlineCompletionSession.getOrNull(topLevelEditor)
        pendingChange = event
        application.invokeLater {
          if (pendingChange !== event) return@invokeLater
          pendingChange = null
          val lookup = event.lookup
          if (editor.isDisposed || lookup.project.isDisposed || LookupManager.getActiveLookup(editor) !== lookup) return@invokeLater
          if (lookup.currentItem !== event.item) return@invokeLater
          if (InlineCompletionSession.getOrNull(topLevelEditor) !== session) return@invokeLater
          processCurrentItemChanged(lookupChanged)
        }
      }

      private fun processCurrentItemChanged(lookupChanged: InlineCompletionEvent.LookupChange) {
        val handler = InlineCompletion.getHandlerOrNull(lookupChanged.topLevelEditor)
        LOG.trace {
          "[Inline Completion] currentItemChanged (clientId=${ClientId.currentOrNull}): " +
          "item='${lookupChanged.event.item?.lookupString}', handler=${if (handler != null) "present" else "null"}"
        }
        if (handler == null) return
        handler.invokeEvent(lookupChanged)
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
