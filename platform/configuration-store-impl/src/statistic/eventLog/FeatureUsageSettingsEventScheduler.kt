// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.configurationStore.ComponentInfo
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.*
import java.util.*
import kotlin.time.Duration.Companion.days

private val PERIOD_DELAY = 1.days

internal class FeatureUsageSettingsEventScheduler : FeatureUsageStateEventTracker {
  override fun initialize() {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    ApplicationManager.getApplication().coroutineScope.launch {
      delay(PERIOD_DELAY)
      while (true) {
        logConfigStateEvents()
        delay(PERIOD_DELAY)
      }
    }
  }

  override suspend fun reportNow() {
    logConfigStateEvents()
  }
}

private suspend fun logConfigStateEvents() {
  if (!FeatureUsageLogger.isEnabled()) {
    return
  }

  coroutineScope {
    launch { logInitializedProjectComponents(ApplicationManager.getApplication()) }
    launch {
      val projectManager = ProjectManagerEx.getInstanceEx()
      val projects = ArrayDeque(projectManager.openProjects.toList())
      if (projectManager.isDefaultProjectInitialized) {
        projects.addFirst(projectManager.defaultProject)
      }

      while (true) {
        val project = projects.pollFirst() ?: break
        if (project.isInitialized && !project.isDisposed) {
          logInitializedProjectComponents(project)
        }
      }
    }
  }
}

private suspend fun logInitializedProjectComponents(componentManager: ComponentManager) {
  val components = ((componentManager.stateStore as? ComponentStoreImpl) ?: return).getComponents()
  return logInitializedComponentsAndContinue(componentManager as? Project, components, ArrayDeque(components.keys))
}

private suspend fun logInitializedComponentsAndContinue(project: Project?,
                                                        components: Map<String, ComponentInfo>,
                                                        names: ArrayDeque<String>) {
  while (true) {
    val nextComponentName = names.pollFirst() ?: return
    logInitializedComponent(project, components.get(nextComponentName) ?: continue, nextComponentName)
    logInitializedComponentsAndContinue(project, components, names)
  }
}

private suspend fun logInitializedComponent(project: Project?, info: ComponentInfo, name: String) {
  val stateSpec = info.stateSpec
  if (stateSpec == null || !stateSpec.reportStatistic) {
    return
  }

  val component = info.component as? PersistentStateComponent<*> ?: return
  withContext(Dispatchers.EDT) {
    try {
      component.state?.let { FeatureUsageSettingsEvents.logConfigurationState(name, it, project) }
    }
    catch (e: Exception) {
      logger<FeatureUsageSettingsEventScheduler>().warn("Error during configuration recording", e)
    }
  }
}

