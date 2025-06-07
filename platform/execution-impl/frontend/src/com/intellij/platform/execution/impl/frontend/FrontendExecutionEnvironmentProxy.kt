// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.execution.rpc.ExecutionEnvironmentProxyDto
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.execution.runners.RunnerAndConfigurationSettingsProxy
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
fun ExecutionEnvironmentProxyDto.executionEnvironment(project: Project, cs: CoroutineScope): ExecutionEnvironmentProxy {
  return FrontendExecutionEnvironmentProxy(project, cs, this)
}

private class FrontendExecutionEnvironmentProxy(
  private val project: Project,
  cs: CoroutineScope,
  private val dto: ExecutionEnvironmentProxyDto,
) : ExecutionEnvironmentProxy {
  private val icon = dto.icon.icon()
  private val rerunIcon = dto.rerunIcon.icon()
  private val isStartingStateFlow = dto.isStarting.toFlow().stateIn(cs, Eagerly, dto.isStartingInitial)

  override fun isShowInDashboard(): Boolean {
    // TODO: implement
    return false
  }

  override fun getRunProfileName(): @NlsSafe String {
    return dto.runProfileName
  }

  override fun getIcon(): Icon {
    return icon
  }

  override fun getRerunIcon(): Icon {
    return rerunIcon
  }

  override fun getRunnerAndConfigurationSettingsProxy(): RunnerAndConfigurationSettingsProxy? {
    // TODO: implement
    return null
  }

  override fun getContentToReuse(): RunContentDescriptor? {
    // TODO: implement
    return null
  }

  override fun isStarting(): Boolean {
    return isStartingStateFlow.value
  }

  override fun isStartingFlow(): Flow<Boolean> {
    return isStartingStateFlow
  }

  override fun performRestart() {
    project.service<FrontendFrontendExecutionEnvironmentProxyCoroutineScope>().cs.launch {
      dto.restartRequest.send(Unit)
    }
  }

  override fun getExecutionEnvironment(): ExecutionEnvironment? {
    return dto.executionEnvironment
  }
}


@Service(Service.Level.PROJECT)
private class FrontendFrontendExecutionEnvironmentProxyCoroutineScope(val cs: CoroutineScope)