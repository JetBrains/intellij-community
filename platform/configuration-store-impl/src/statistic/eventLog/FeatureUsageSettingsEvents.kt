// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.jdom.Element
import java.util.*

private val LOG = Logger.getInstance("com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEventPrinter")
private const val RECORDER_ID = "settings."

object FeatureUsageSettingsEvents {
  val printer = FeatureUsageSettingsEventPrinter()

  fun logDefaultConfigurationState(componentName: String, stateSpec: State, clazz: Class<*>, project: Project?) {
    if (stateSpec.reportStatistic && FeatureUsageLogger.isEnabled()) {
      printer.logDefaultConfigurationState(componentName, clazz, project)
    }
  }

  fun logConfigurationState(componentName: String, stateSpec: State, state: Any?, project: Project?) {
    if (stateSpec.reportStatistic && FeatureUsageLogger.isEnabled()) {
      printer.logConfigurationState(componentName, state, project)
    }
  }
}

open class FeatureUsageSettingsEventPrinter {
  private val defaultFilter = SkipDefaultsSerializationFilter()

  fun logDefaultConfigurationState(componentName: String, clazz: Class<*>, project: Project?) {
    try {
      val default = defaultFilter.getDefaultValue(clazz)
      logConfigurationState(componentName, default, project)
    }
    catch (e: Exception) {
      LOG.warn("Cannot initialize default settings for '$componentName'")
    }
  }

  fun logConfigurationState(componentName: String, state: Any?, project: Project?) {
    if (state == null || state is Element) {
      return
    }

    val accessors = BeanBinding.getAccessors(state.javaClass)
    if (accessors.isEmpty()) {
      return
    }

    val groupId = RECORDER_ID + componentName
    val isDefaultProject = project?.isDefault == true
    val hash = if (!isDefaultProject) toHash(project?.name) else null

    for (accessor in accessors) {
      val type = accessor.genericType
      if (type === Boolean::class.javaPrimitiveType) {
        val value = accessor.read(state)
        val isNotDefault = defaultFilter.accepts(accessor, state)
        val content = HashMap<String, Any>()
        content["value"] = value
        if (isNotDefault) {
          content["default"] = false
        }

        if (isDefaultProject) {
          content["default_project"] = true
        }
        else {
          hash?.let {
            content["project"] = hash
          }
        }
        logConfig(groupId, accessor.name, content)
      }
    }
  }

  protected open fun logConfig(groupId: String, eventId: String, data: Map<String, Any>) {
    FeatureUsageLogger.log(groupId, eventId, data)
  }

  internal fun toHash(projectName: String?): String? {
    return projectName?.let {
      return projectName.hashCode().toString()
    }
  }
}
