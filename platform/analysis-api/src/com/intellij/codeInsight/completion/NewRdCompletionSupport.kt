// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NewRdCompletionSupport {
  fun isFrontendRdCompletionOnImpl(): Boolean

  /**
   * Schedule autopopup for the given editor and completion type.
   */
  fun scheduleAutopopupOnFrontend(project: Project, editor: Editor, completionType: CompletionType): Boolean

  /**
   * @return true if this a client process of a remoteDev session in IntelliJ IDEA specifically (and NOT another IDE like PyCharm, PhpStorm, etc.).
   */
  fun isFrontendForIntelliJBackend(): Boolean

  /**
   * return true if this lookup is handled by new remdev logic
   * - on the frontend side, it is not supported atm, feel free to implement it if necessary
   * - on the backend side, it means that the lookup is created by `BackendCompletionLookupMirror`
   */
  fun isNewFrontendLookup(lookup: Lookup): Boolean

  /**
   * Notifies completion support that this editor does not have PSI on the current IDE part (BE, FE, Monolith)
   *
   * This notification makes it possible to notify BE that we don't have PSI for this editor on FE. Thus, completion should start on the backend.
   * Is NOOP on Backend and in Monolith mode.
   */
  fun noPsiAvailable(editor: Editor)

  fun isBackendCompletionActionAvailableImpl(editor: Editor): Boolean

  companion object {
    /**
     * @return true if `remdev.completion.on.frontend` registry flag is enabled AND this is a host or a client of a remoteDev session.
     */
    @JvmStatic
    fun isFrontendRdCompletionOn(): Boolean = getInstance().isFrontendRdCompletionOnImpl()

    /**
     * @return logs an error with a given [message] if `remdev.completion.on.frontend.report.suboptimal.usage` registry flag is enabled.
     */
    @JvmStatic
    fun reportSuboptimalUsage(message: () -> String) {
      if (Registry.`is`("remdev.completion.on.frontend.report.suboptimal.usage")) {
        logger<NewRdCompletionSupport>().error(message())
      }
    }

    @JvmStatic
    fun getInstance(): NewRdCompletionSupport {
      return serviceOrNull<NewRdCompletionSupport>() ?: NoOpNewCompletionSupport
    }

    @JvmStatic
    fun isBackendCompletionActionAvailableInEditor(editor: Editor): Boolean = getInstance().isBackendCompletionActionAvailableImpl(editor)
  }
}

private object NoOpNewCompletionSupport : NewRdCompletionSupport {
  override fun isFrontendRdCompletionOnImpl(): Boolean = false

  override fun scheduleAutopopupOnFrontend(project: Project, editor: Editor, completionType: CompletionType) = false

  override fun isFrontendForIntelliJBackend(): Boolean = false

  override fun isNewFrontendLookup(lookup: Lookup): Boolean = false

  override fun noPsiAvailable(editor: Editor) {}

  override fun isBackendCompletionActionAvailableImpl(editor: Editor): Boolean = false
}