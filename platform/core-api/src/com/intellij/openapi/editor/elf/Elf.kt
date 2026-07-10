// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Entry point for the experimental Editor Lock-Free (elf) typing infrastructure.
 *
 * Elf keeps a UI-side document copy for text typed in an editor and synchronizes
 * it back to the real document after the typing scope. The real document remains
 * authoritative for PSI, persistence, undo, and integrations that require committed
 * document state.
 *
 * An elf scope is thread-local: changes made to an elf document inside the scope
 * are not observable from background threads until they are synchronized to the
 * real document.
 *
 * This API is intentionally a no-op when [ElfFeatureFlag] is disabled or when the
 * platform implementation is not available. In that mode [getElfDocument] and
 * [getRealDocument] return the original document, [withElfScope] simply runs the
 * action, and PSI interaction is allowed.
 *
 * The current implementation supports only pure text editing. Code that needs PSI
 * or committed document semantics should check [isPsiInteractionAllowed] before
 * touching PSI from typing-time code.
 */
interface Elf {

  /**
   * Runs [action] inside an elf typing scope.
   *
   * Inside this scope editor text changes may be applied to the elf document first
   * and synchronized to the real document when the outermost scope finishes. The
   * scope is local to the EDT execution that entered it, so background threads
   * remain outside the scope and cannot observe these elf document changes before
   * synchronization. The scope must be entered on EDT.
   *
   * Example typing code inside the scope may update the editor document without
   * taking the application write lock:
   *
   * ```
   * Elf.getElf().withElfScope(() -> {
   *   editor.getDocument().insertString(offset, text);
   * });
   * ```
   */
  @RequiresEdt
  fun withElfScope(@RequiresEdt action: Runnable)

  /**
   * Returns `true` when the current EDT execution is inside [withElfScope].
   */
  fun isInElfScope(): Boolean

  /**
   * Returns whether typing-time code may interact with PSI in the current context.
   *
   * This is currently `false` inside an elf scope because lock-free PSI is not
   * integrated yet. Callers that need PSI should skip their smart behavior instead
   * of forcing document commit or PSI access from the lock-free typing path.
   */
  fun isPsiInteractionAllowed(): Boolean

  /**
   * Returns the UI-side elf document corresponding to [document], or [document]
   * itself when elf is disabled or unsupported for this document.
   *
   * This method is mostly intended for UI code such as editor painting and layout,
   * which should observe the elf document regardless of whether the current code is
   * inside an elf scope.
   */
  fun getElfDocument(document: Document): Document

  /**
   * Returns the authoritative real document corresponding to [document], or
   * [document] itself when no elf wrapper is involved.
   */
  fun getRealDocument(document: Document): Document

  /**
   * Schedules [action] to run after the outermost elf scope finishes.
   *
   * This method may be called only from inside [withElfScope]. It is intended for
   * work that must observe the real document after elf changes have been synchronized.
   */
  fun performOnScopeFinished(action: Runnable)

  /**
   * Returns whether the command currently being executed was started by
   * [executeElfCommand].
   */
  fun isElfCommandInProgress(): Boolean

  /**
   * Executes [command] as an editor typing command and marks it as an elf command
   * for the duration of execution.
   */
  fun executeElfCommand(
    commandProject: Project?,
    commandName: @Command String?,
    commandGroupId: Any?,
    command: Runnable,
  )

  companion object {
    @JvmStatic
    fun getElf(): Elf {
      if (!ElfFeatureFlag.isEnabled()) {
        return OffDuty
      }
      val application = ApplicationManager.getApplication()
      return application?.serviceOrNull<Elf>() ?: OffDuty
    }
  }
}

/**
 * OffDuty is used when feature flag disabled or actual implementation from
 * platform-impl does not exist
 *
 * @see ElfFeatureFlag
 */
private object OffDuty : Elf {
  override fun withElfScope(action: Runnable) {
    action.run()
  }

  override fun isInElfScope(): Boolean {
    return false
  }

  override fun isPsiInteractionAllowed(): Boolean {
    return true
  }

  override fun getElfDocument(document: Document): Document {
    return document
  }

  override fun getRealDocument(document: Document): Document {
    return document
  }

  override fun performOnScopeFinished(action: Runnable) {
  }

  override fun isElfCommandInProgress(): Boolean {
    return false
  }

  override fun executeElfCommand(
    commandProject: Project?,
    commandName: @Command String?,
    commandGroupId: Any?,
    command: Runnable,
  ) {
    command.run()
  }
}
