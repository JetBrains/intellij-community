// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlin.concurrent.Volatile

/**
 * Entry point for the experimental Editor Lock-Free (elf) typing infrastructure.
 *
 * Elf keeps a UI-side document copy for text typed in an editor and synchronizes
 * it back to the real document after the typing scope. The real document remains
 * authoritative for PSI, persistence, undo, and integrations that require committed
 * document state.
 *
 * An elf scope is confined to EDT: changes made to an elf document inside the
 * scope are not observable from background threads until they are synchronized to
 * the real document.
 *
 * This API is a no-op only when the platform implementation is not available. In
 * that mode [getElfDocument] and [getRealDocument] return the original document,
 * [withElfScope] simply runs the action, and the unsupported-operation guard is
 * never active.
 *
 * The platform implementation, however, is registered unconditionally and does not
 * check [ElfFeatureFlag]: [withElfScope] enters an effective elf scope even while
 * the flag is disabled. Such a scope still activates the unsupported-operation
 * guard and routes document changes to the elf view, yet with the flag disabled no
 * listener is notified of them through the regular document-changed callbacks until
 * the asynchronous synchronization to the real document. Callers must therefore check [ElfFeatureFlag.isEnabled] before entering
 * an elf scope, as the editor typing path does.
 *
 * The current implementation supports only pure text editing. Code that needs
 * operations requiring locking (PSI, workspace model, virtual files, etc.) should
 * check [isUnsupportedOperationGuardActive] before performing them from typing-time code.
 */
interface Elf {

  /**
   * Runs [action] inside an elf typing scope.
   *
   * Inside this scope editor text changes may be applied to the elf document first;
   * their synchronization to the real document is scheduled when the outermost
   * scope finishes and completes asynchronously. The scope covers the whole EDT
   * while it is active — including code reached through reentrant event dispatch —
   * whereas background threads remain outside the scope and cannot observe these
   * elf document changes before synchronization. The scope must be entered on EDT.
   *
   * Example typing code inside the scope may update the editor document without
   * taking the application write lock:
   *
   * ```
   * Elf.getElf().withElfScope {
   *   editor.document.insertString(offset, text)
   * }
   * ```
   *
   * This method is not gated by [ElfFeatureFlag]; the caller is responsible for
   * checking [ElfFeatureFlag.isEnabled] before entering the scope.
   */
  @RequiresEdt
  fun <T> withElfScope(@RequiresEdt action: () -> T): T

  /**
   * Returns `true` when the current EDT execution is inside [withElfScope].
   */
  fun isInElfScope(): Boolean

  /**
   * Returns `true` when the current execution is inside lock-free typing and must not
   * call operations that are not supported there yet.
   *
   * Some operations require locking, so they cannot be used during lock-free typing:
   * PSI, document commit, workspace model, virtual files, etc. Callers should check
   * this guard and skip their smart behavior instead of performing such operations
   * from the lock-free typing path.
   *
   * This guard is temporary and will be removed once all operations are supported
   * or reworked for lock-free typing.
   */
  fun isUnsupportedOperationGuardActive(): Boolean

  /**
   * Returns the UI-side elf document corresponding to [document], or [document]
   * itself when no elf view exists for it (for example, a document created for
   * non-AWT use). This method is not gated by [ElfFeatureFlag]: a separate elf
   * view is returned even while the flag is disabled.
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
   * Schedules [action] to run on EDT right after the outermost elf scope finishes,
   * even when the scope's action fails with an exception.
   *
   * This method may be called only from inside [withElfScope]. The action runs
   * before pending elf changes are synchronized to the real document; it is
   * intended for work that must start only outside an elf scope, such as launching
   * the asynchronous elf-to-real synchronization pass.
   */
  fun performOnScopeFinished(action: Runnable)

  /**
   * Returns whether the command currently being executed was started by
   * [executeElfCommand].
   */
  fun isElfCommandInProgress(): Boolean

  /**
   * Executes [command] through the command processor and marks it as an elf
   * command for the duration of execution, so [isElfCommandInProgress] returns
   * `true` inside it.
   */
  fun executeElfCommand(
    commandProject: Project?,
    commandName: @Command String?,
    commandGroupId: Any?,
    command: Runnable,
  )

  /**
   * Runs [action] in place when the current execution is inside an elf scope;
   * otherwise runs it under the application read lock.
   */
  fun <T> runReadAction(action: () -> T): T

  /**
   * Runs [action] in place when the current execution is inside an elf scope;
   * otherwise runs it under the application write lock.
   */
  fun runWriteAction(action: Runnable)

  /**
   * Does nothing when the current execution is inside an elf scope; otherwise
   * asserts application write access.
   */
  fun assertWriteAllowed()

  companion object {
    /**
     * Caches the resolved application service to keep [getElf] cheap on hot document paths;
     * [ApplicationManager.registerCleaner] drops the cache when the application is replaced.
     * The [OffDuty] fallback is intentionally never cached: resolving too early — before the
     * application or the service exists — must not pin the no-op implementation for the rest
     * of the session.
     */
    @Volatile private var ELF: Elf? = null

    init {
      ApplicationManager.registerCleaner { ELF = null }
    }

    @JvmStatic
    fun getElf(): Elf {
      ELF?.let { return it }
      val application = ApplicationManager.getApplication() ?: return OffDuty
      val elf = application.serviceOrNull<Elf>() ?: return OffDuty
      ELF = elf
      return elf
    }
  }
}

/**
 * OffDuty is used when the actual implementation from platform-impl does not exist.
 * [ElfFeatureFlag] does not affect this choice: when the platform implementation is
 * registered, it is used even while the flag is disabled.
 */
private object OffDuty : Elf {

  override fun <T> withElfScope(action: () -> T): T {
    return action.invoke()
  }

  override fun isInElfScope(): Boolean {
    return false
  }

  override fun isUnsupportedOperationGuardActive(): Boolean {
    return false
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

  override fun <T> runReadAction(action: () -> T): T {
    return runReadActionBlocking(action)
  }

  override fun runWriteAction(action: Runnable) {
    ApplicationManager.getApplication().runWriteAction(action)
  }

  override fun assertWriteAllowed() {
    ThreadingAssertions.assertWriteAccess()
  }
}
