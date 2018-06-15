// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.concurrency.JobScheduler
import com.intellij.configurationStore.ComponentStoreImpl
import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEvents.logConfigurationState
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.ArrayUtilRt
import java.util.concurrent.TimeUnit

class FeatureUsageSettingsEventScheduler : StartupActivity {
  private val LOG = Logger.getInstance("com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEventScheduler")

  private val PERIOD_DELAY = 24 * 60
  private val INITIAL_DELAY = PERIOD_DELAY

  override fun runActivity(project: Project) {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { logConfigStateEvents() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  private fun logConfigStateEvents() {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    logInitializedComponents(ApplicationManager.getApplication())
    logInitializedComponents(ProjectManager.getInstance().defaultProject)
    ProjectManager.getInstance().openProjects.filter { project -> !project.isDefault }.forEach { project ->
      logInitializedComponents(project)
    }
  }

  private fun logInitializedComponents(componentManager: ComponentManager) {
    if (componentManager.stateStore !is ComponentStoreImpl) {
      return
    }

    val project = componentManager as? Project
    ApplicationManager.getApplication().invokeLater {
      val components = (componentManager.stateStore as ComponentStoreImpl).getComponents()
      for (name in ArrayUtilRt.toStringArray(components.keys)) {
        val info = components[name]
        val component = info?.component
        val spec = info?.stateSpec
        if (spec?.reportStatistic == true && component is PersistentStateComponent<*>) {
          logConfigurationState(name, spec, getState(component), project)
        }
      }
    }
  }

  private fun getState(component: PersistentStateComponent<*>): Any? {
    return try {
      component.state
    }
    catch (e: Throwable) {
      LOG.warn("Error during configuration recording", e)
      null
    }
  }
}

