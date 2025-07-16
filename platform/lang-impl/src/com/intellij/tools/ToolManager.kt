// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer.LightCustomizeStrategy
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeProcessor
import com.intellij.tools.ToolManager.Companion.getInstance
import org.jetbrains.annotations.ApiStatus

@Service
class ToolManager : BaseToolManager<Tool>(SchemeManagerFactory.getInstance(), "tools", ToolsBundle.message("tools.settings")) {
  companion object {
    @JvmStatic
    fun getInstance(): ToolManager = service<ToolManager>()
  }

  override fun createProcessor(): SchemeProcessor<ToolsGroup<Tool>, ToolsGroup<Tool>> {
    return object : ToolsProcessor<Tool>() {
      override fun createToolsGroup(groupName: String) = ToolsGroup<Tool>(groupName)

      override fun createTool(): Tool = Tool()
    }
  }

  @ApiStatus.Internal
  override fun getActionIdPrefix(): String = Tool.ACTION_ID_PREFIX

  @ApiStatus.Internal
  public override fun getGroupIdPrefix(): String = "Tools_"
}

private class ToolManagerActionTuner : ActionConfigurationCustomizer, LightCustomizeStrategy {
  override suspend fun customize(actionRegistrar: ActionRuntimeRegistrar) {
    getInstance().registerActions(actionRegistrar)
  }
}
