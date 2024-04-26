// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore.statistic.eventLog

import com.intellij.configurationStore.ComponentInfo
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.getStateForComponent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageStateEventTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.days

private val PERIOD_DELAY = 1.days

internal class FeatureUsageSettingsEventScheduler : FeatureUsageStateEventTracker {
  override fun initialize() {
    (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch {
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
      @Suppress("TestOnlyProblems")
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
  val project = componentManager as? Project
  @Suppress("IfThenToElvis")
  return logInitializedComponentsAndContinue(
    project = project,
    components = components,
    names = ArrayDeque(components.keys),
    reporter = if (project == null) service<FeatureUsageSettingsEvents>() else project.service<FeatureUsageSettingsEvents>(),
  )
}

private suspend fun logInitializedComponentsAndContinue(project: Project?,
                                                        components: Map<String, ComponentInfo>,
                                                        names: ArrayDeque<String>,
                                                        reporter: FeatureUsageSettingsEvents) {
  while (true) {
    val nextComponentName = names.pollFirst() ?: return
    logInitializedComponent(info = components.get(nextComponentName) ?: continue,
                            name = nextComponentName,
                            reporter = reporter)
    logInitializedComponentsAndContinue(project = project, components = components, names = names, reporter = reporter)
  }
}

private suspend fun logInitializedComponent(info: ComponentInfo, name: String, reporter: FeatureUsageSettingsEvents) {
  val stateSpec = info.stateSpec
  if (stateSpec == null || !stateSpec.reportStatistic) {
    return
  }

  val component = info.component as? PersistentStateComponent<*> ?: return
  try {
    getStateForComponent(component, stateSpec)?.let {
      reporter.logConfigurationState(componentName = name, state = it)
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    logger<FeatureUsageSettingsEventScheduler>().warn("Error during configuration recording", e)
  }
}

