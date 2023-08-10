// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

/**
 * Performs lazy initialization of a tool window registered in `plugin.xml`.
 * Please implement [com.intellij.openapi.project.DumbAware] marker interface to indicate that the tool window content should be
 * available during the indexing process.
 *
 * To localizing tool window stripe title, add key `toolwindow.stripe.yourToolWindowId.replace(" ", "_")` to plugin's resource bundle.
 *
 * See [Tool Windows](https://www.jetbrains.org/intellij/sdk/docs/user_interface_components/tool_windows.html) in SDK Docs.
 */
interface ToolWindowFactory {
  suspend fun isApplicableAsync(project: Project): Boolean {
    return blockingContext {
      @Suppress("DEPRECATION")
      isApplicable(project)
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use isApplicableAsync")
  fun isApplicable(project: Project): Boolean = true

  fun createToolWindowContent(project: Project, toolWindow: ToolWindow)

  /**
   * Perform additional initialization routine here.
   */
  fun init(toolWindow: ToolWindow) {}

  /**
   * Check if tool window (and its stripe button) should be visible after startup.
   *
   * @see ToolWindow.isAvailable
   */
  fun shouldBeAvailable(project: Project): Boolean = true

  @Suppress("DeprecatedCallableAddReplaceWith")
  @get:Deprecated("Use {@link ToolWindowEP#isDoNotActivateOnStart}")
  val isDoNotActivateOnStart: Boolean
    get() = false

  /**
   * Return custom anchor or null to use anchor defined in Tool Window Registration or customized by user.
   */
  @get:ApiStatus.Internal
  val anchor: ToolWindowAnchor?
    get() = null

  @get:ApiStatus.Internal
  val icon: Icon?
    get() = null
}
