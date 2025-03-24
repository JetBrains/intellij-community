// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

/**
 * Performs lazy initialization of a tool window registered in `plugin.xml`.
 *
 * For localizing the tool window stripe title, add key `toolwindow.stripe.yourToolWindowId.replace(" ", "_")` to plugin's resource bundle.
 *
 * See [Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html) in SDK Docs.
 */
interface ToolWindowFactory : PossiblyDumbAware {
  /**
   * This will be called once, and cannot be undone.
   *
   * @return false to deactivate the factory
   */
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

  // todo it acts like ProjectActivity.execute - we should find a better name for this method
  @Experimental
  @Internal
  suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
  }

  /**
   * Check if the tool window (and its stripe button) should be visible after startup.
   * Unavailable tool windows are still visible in the "Main Menu | View | Tool Windows".
   *
   * This will be called once. Use [ToolWindow.setAvailable] on state changes.
   *
   * @see ToolWindow.isAvailable
   * @see ToolWindowManager.unregisterToolWindow
   */
  fun shouldBeAvailable(project: Project): Boolean = true

  @Suppress("DeprecatedCallableAddReplaceWith")
  @get:Deprecated("Use {@link ToolWindowEP#isDoNotActivateOnStart}")
  val isDoNotActivateOnStart: Boolean
    get() = false

  /**
   * Return custom anchor or null to use anchor defined in Tool Window Registration or customized by user.
   */
  @get:Internal
  val anchor: ToolWindowAnchor?
    get() = null

  @get:Internal
  val icon: Icon?
    get() = null
}
