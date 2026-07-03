// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.elf.ElfFeatureFlag
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Historically, Editor models required read lock for reading them.
 * We are planning to relax the contracts and allow interacting with Editor from EDT without any locks.
 * Hence, editor models should use this class to ensure consistency.
 */
@ApiStatus.Experimental
interface EditorThreading {
  companion object {

    /**
     * Checks that interaction with the editor is allowed in the current context
     */
    @JvmStatic
    fun assertInteractionAllowed() {
      if (!EDT.isCurrentThreadEdt() && !ApplicationManager.getApplication().isReadAccessAllowed) {
        throw IllegalStateException("Access to Editor models (caret, selection, etc.) is allowed either from EDT, or under read action. Current thread: ${Thread.currentThread()}")
      }
    }

    /**
     * Adjust the context for editor accessing operation [action].
     * [action] is always executed in place.
     */
    @JvmStatic
    fun <T, E : Throwable> compute(action: ThrowableComputable<T, E>): T {
      return if (EDT.isCurrentThreadEdt()) {
        action.compute()
      }
      else {
        ApplicationManager.getApplication().runReadAction(action)
      }
    }

    /**
     * Adjust the context for editor accessing operation [action].
     * [action] is always executed in place.
     */
    @JvmStatic
    fun run(action: Runnable) {
      return if (EDT.isCurrentThreadEdt()) {
        action.run()
      }
      else {
        ApplicationManager.getApplication().runReadAction(action)
      }
    }

    @Internal
    @JvmStatic
    fun <T, E : Throwable> computeWritable(action: ThrowableComputable<T, E>): T {
      return if (ElfFeatureFlag.isEnabled() && EDT.isCurrentThreadEdt()) {
        action.compute()
      }
      else {
        WriteIntentReadAction.computeThrowable(action)
      }
    }

    @Internal
    @JvmStatic
    fun runWritable(action: Runnable) {
      if (ElfFeatureFlag.isEnabled() && EDT.isCurrentThreadEdt()) {
        action.run()
      }
      else {
        WriteIntentReadAction.run(action)
      }
    }

    @Internal
    @JvmStatic
    fun assertWriteAllowed() {
      if (ElfFeatureFlag.isEnabled() && EDT.isCurrentThreadEdt()) {
        return
      }
      ThreadingAssertions.assertWriteAccess()
    }

    @Internal
    @JvmStatic
    fun write(action: Runnable) {
      if (ElfFeatureFlag.isEnabled() && EDT.isCurrentThreadEdt() && ModalityState.current() == ModalityState.nonModal()) {
        action.run()
      }
      else {
        /**
         * Modal dialogs hold WIL on EDT, preventing BGT WA from be started.
         * See usages of com.intellij.openapi.ui.impl.AbstractDialog.show.
         * It means if an editor is inside a modal dialog (e.g., Create Branch from <branch>),
         * then the corresponding ui document can't be synced with the real one until the editor is closed,
         * which makes no sense.
         */
        ApplicationManager.getApplication().runWriteAction(action)
      }
    }
  }
}
