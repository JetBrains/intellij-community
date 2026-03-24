// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.components.service
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

  fun isFrontendForIntelliJBackend(): Boolean

  /**
   * return true if this lookup is handled by new remdev logic
   * - on the frontend side, it means that the lookup is created by fair completion machinery
   * - on the backend side, it means that the lookup is created by BackendCompletionLookupMirror
   */
  fun isNewFrontendLookup(lookup: Lookup): Boolean

  /**
   * Notifies completion support that this editor does not have PSI on the current IDE part (BE, FE, Monolith)
   *
   * This notification makes it possible to notify BE that we don't have PSI for this editor on FE. Thus, completion should start on the backend.
   * Is NOOP on Backend and in Monolith mode.
   */
  fun noPsiAvailable(editor: Editor)

  companion object {
    @JvmStatic
    fun isFrontendRdCompletionOn(): Boolean = service<NewRdCompletionSupport>().isFrontendRdCompletionOnImpl()

    @JvmStatic
    fun reportSuboptimalUsage(message: () -> String) {
      if (Registry.`is`("remdev.completion.on.frontend.force.errors")) {
        logger<NewRdCompletionSupport>().error(message())
      }
    }

    @JvmStatic
    fun getInstance(): NewRdCompletionSupport = service<NewRdCompletionSupport>()
  }
}

internal class NoOpNewCompletionSupport : NewRdCompletionSupport {
  override fun isFrontendRdCompletionOnImpl(): Boolean = false

  override fun scheduleAutopopupOnFrontend(project: Project, editor: Editor, completionType: CompletionType) = false

  override fun isFrontendForIntelliJBackend(): Boolean = false

  override fun isNewFrontendLookup(lookup: Lookup): Boolean = false

  override fun noPsiAvailable(editor: Editor) {}
}