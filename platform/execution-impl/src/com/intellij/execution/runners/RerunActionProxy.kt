// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import org.jetbrains.annotations.ApiStatus

/**
 * This interface abstracts the functionality needed by [FakeRerunAction],
 * so it can be implemented differently on frontend and backend sides.
 */
@ApiStatus.Internal
interface RerunActionProxy {
  /**
   * Checks if the delegate should be used for the [FakeRerunAction]
   */
  fun isApplicable(event: AnActionEvent): Boolean

  fun getExecutionEnvironmentProxy(event: AnActionEvent): ExecutionEnvironmentProxy?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RerunActionProxy> = create<RerunActionProxy>("com.intellij.execution.rerunActionProxy")
  }
}