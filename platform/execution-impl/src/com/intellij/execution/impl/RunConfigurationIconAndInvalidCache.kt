// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.NonUrgentExecutor
import javax.swing.Icon
import kotlin.math.max

internal class RunConfigurationIconAndInvalidCache {
  private data class ConfigurationInfo(val icon: Icon, val isInvalid: Boolean, val finishTime: Long, val calculationTime: Long)

  private val resultMap = ConcurrentCollectionFactory.createConcurrentMap<String, ConfigurationInfo>()

  fun remove(id: String) {
    resultMap.remove(id)
  }

  fun get(id: String, settings: RunnerAndConfigurationSettings, project: Project): Icon {
    return resultMap.getOrPut(id) {
      val icon = settings.configuration.icon ?: AllIcons.Actions.Help
      recalculateIcon(id, project)
      ConfigurationInfo(icon, false, System.currentTimeMillis(), 0)
    }.icon
  }

  fun isInvalid(id: String) : Boolean {
    return resultMap[id]?.isInvalid ?: false
  }


  fun clear() {
    resultMap.clear()
  }

  fun checkValidity(id: String, project: Project) {
    val configurationInfo = resultMap[id]
    val expired = configurationInfo == null ||
                  (System.currentTimeMillis() - configurationInfo.finishTime) > (max(configurationInfo.calculationTime, 150L) * 10)

    if (expired) {
      recalculateIcon(id, project)
    }
  }

  private data class IconCalcResult(val icon: Icon, val isInvalid: Boolean, val startTime: Long)

  private fun recalculateIcon(id: String, project: Project) {
    ReadAction.nonBlocking<IconCalcResult?> {
      val runManagerImpl = RunManagerImpl.getInstanceImpl(project)
      val settings = runManagerImpl.getConfigurationById(id) ?: return@nonBlocking null
      val startTime = System.currentTimeMillis()
      val (icon, invalid) = try {
        settings.checkSettings()
        ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
      }
      catch (e: IndexNotReadyException) {
        ProgramRunnerUtil.getConfigurationIcon(settings, false) to false
      }
      catch (ignored: RuntimeConfigurationException) {
        val invalid = !DumbService.isDumb(runManagerImpl.project)
        ProgramRunnerUtil.getConfigurationIcon(settings, invalid) to invalid
      }
      IconCalcResult(icon, invalid, startTime)
    }.expireWith(project)
      .coalesceBy(this, id)
      .submit(NonUrgentExecutor.getInstance())
      .onSuccess {
        if (it == null) {
          resultMap.remove(id)
        }
        else {
          val finisTime = System.currentTimeMillis()
          resultMap[id] = ConfigurationInfo(it.icon, it.isInvalid, finisTime, finisTime - it.startTime)
        }
      }
  }
}