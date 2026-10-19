// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point (`com.intellij.multiverse.codeInsightContextProvider`) for registering [CodeInsightContext]s.
 *
 * Every file has one **owning** provider. It is the first provider, in registration order, that claims the file; use
 * the `order` attribute to control that order. Only the contexts, the preferred context and the reset action of that
 * provider are used for the file. Providers are not merged.
 *
 * @see CodeInsightContextManager.registerTestOnlyCodeInsightContextProvider for testing
 */
@ApiStatus.OverrideOnly
interface CodeInsightContextProvider {
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  fun getContexts(file: VirtualFile, project: Project): Answer

  /** `true` when [context] is one this provider produces. A plain type test, and no two providers may accept the same context. */
  fun isOwnerOf(context: CodeInsightContext): Boolean

  /**
   * The user picked [context] for [file] in the multiverse switcher. Only the owning provider is told, and only for a
   * real pick, never for a context the platform inferred.
   */
  fun onContextPicked(file: VirtualFile, project: Project, context: CodeInsightContext) {}

  fun subscribeToChanges(project: Project, invalidator: Invalidator)

  /**
   * The preferred context for [file] out of [contexts]. The platform uses it when nothing else has selected a context.
   * Return `null` to let the platform take the first context. Only the owning provider is asked. This must be a plain
   * query: it runs on a hot path and its result is cached.
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  fun getPreferredContext(file: VirtualFile, project: Project, contexts: List<CodeInsightContext>): CodeInsightContext? = null

  /**
   * Clears an override on [file], such as a per-file pin, so that the file follows the provider's own answer again.
   * Returns `null` when there is nothing to reset. Only the owning provider is asked, and the switcher shows an icon
   * button while this is not null.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun getContextResetAction(file: VirtualFile, project: Project): ContextResetAction? = null

  @ApiStatus.NonExtendable
  interface Invalidator {
    /** Drops the cached contexts of every file. */
    @RequiresWriteLock
    fun requestInvalidation()

    /**
     * Checks the contexts of [files] again and drops only the changed ones. An empty [files] does nothing.
     * Any thread, no lock.
     */
    fun requestInvalidation(files: Collection<VirtualFile>)
  }

  /** What [getContexts] returns for a file. */
  sealed interface Answer {
    /** This provider does not own the file. The platform asks the next provider. */
    data object NotOwned : Answer

    /** The contexts of the file. [contexts] is never empty. */
    class Contexts(val contexts: List<CodeInsightContext>) : Answer {
      init {
        require(contexts.isNotEmpty()) { "an owned file with no contexts of its own is DefaultContext" }
      }
    }

    companion object {
      /** [Contexts] with only the default context. */
      val DefaultContext: Contexts = Contexts(listOf(defaultContext()))
    }
  }
}

/** Clears one file's context override. See [CodeInsightContextProvider.getContextResetAction]. */
typealias ContextResetAction = suspend () -> Unit