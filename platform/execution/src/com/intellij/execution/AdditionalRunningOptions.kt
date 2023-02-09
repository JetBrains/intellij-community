// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AdditionalRunningOptions {
  fun getAdditionalActions(settings: RunnerAndConfigurationSettings?, isWidget: Boolean): ActionGroup

  companion object {
    @JvmStatic
    fun getInstance(project: Project): AdditionalRunningOptions = project.getService(AdditionalRunningOptions::class.java)
  }
}

private class EmptyAdditionalRunningOptions : AdditionalRunningOptions {
  override fun getAdditionalActions(settings: RunnerAndConfigurationSettings?, isWidget: Boolean): ActionGroup = ActionGroup.EMPTY_GROUP
}