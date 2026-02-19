// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

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
   *
   * It is expected to be called only from RD Backend.
   */
  fun scheduleAutopopup(project: Project, editor: Editor, completionType: CompletionType)

  fun isFrontendForIntelliJBackendImpl(): Boolean

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

    fun isFrontendForIntelliJBackend(): Boolean =
      getInstance().isFrontendForIntelliJBackendImpl()
  }
}

internal class NoOpNewCompletionSupport : NewRdCompletionSupport {
  override fun isFrontendRdCompletionOnImpl(): Boolean = false

  override fun scheduleAutopopup(project: Project, editor: Editor, completionType: CompletionType) =
    throw UnsupportedOperationException("Is not expected to be called, use AutopopupController directly instead")

  override fun isFrontendForIntelliJBackendImpl(): Boolean = false
}