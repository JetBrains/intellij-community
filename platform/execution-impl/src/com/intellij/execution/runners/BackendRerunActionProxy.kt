// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.execution.ui.RunContentManager
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import org.jetbrains.annotations.ApiStatus

/**
 * Backend implementation of [RerunActionPeoxy] that uses the actual execution classes.
 */
@ApiStatus.Internal
private class BackendRerunActionProxy : RerunActionProxy {
  override fun getExecutionEnvironmentProxy(event: AnActionEvent): ExecutionEnvironmentProxy? {
    val environment = getEnvironment(event) ?: return null
    return BackendExecutionEnvironmentProxy(environment)
  }

  override fun getRunContentDescriptorProxy(event: AnActionEvent): RunContentDescriptorProxy? {
    val descriptor = event.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR) ?: return null
    return BackendRunContentDescriptorProxy(descriptor)
  }

  private fun getEnvironment(event: AnActionEvent): ExecutionEnvironment? {
    var environment = event.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT)
    if (environment == null) {
      val project = event.project
      val runContentManager = if (project == null) null else RunContentManager.getInstanceIfCreated(project)
      val contentDescriptor = runContentManager?.getSelectedContent()
      if (contentDescriptor != null) {
        val component = contentDescriptor.component
        if (component != null) {
          environment = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component))
        }
      }
    }
    return environment
  }

  override fun isApplicable(event: AnActionEvent): Boolean {
    return true
  }
}