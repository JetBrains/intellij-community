// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SkipReportingStatistics
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.BeanBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

private val GROUP = EventLogGroup("settings", 9)
private const val CHANGES_GROUP = "settings.changes"
private const val ID_FIELD = "id"

private val recordedComponents = ConcurrentHashMap.newKeySet<String>()
private val recordedOptionNames = ConcurrentHashMap.newKeySet<String>()

internal fun isComponentNameWhitelisted(name: String): Boolean {
  return recordedComponents.contains(name)
}

internal fun isComponentOptionNameWhitelisted(name: String): Boolean {
  return recordedOptionNames.contains(name)
}

private sealed interface LogRequest

private class LogConfigurationState(@JvmField val componentName: String, @JvmField val state: Any) : LogRequest
private class LogConfigurationStateChanged(@JvmField val componentName: String, @JvmField val state: Any) : LogRequest
private class LogDefaultConfigurationState(@JvmField val componentName: String, @JvmField val aClass: Class<*>) : LogRequest


@Service(Service.Level.APP, Service.Level.PROJECT)
internal class FeatureUsageSettingsEvents private constructor(private val project: Project?, coroutineScope: CoroutineScope) {
  private val channel = Channel<LogRequest>(capacity = Channel.UNLIMITED)

  @Suppress("unused")
  constructor(coroutineScope: CoroutineScope) : this(project = null, coroutineScope = coroutineScope)

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      channel.close()
    }
    else {
      val printer = FeatureUsageSettingsEventPrinter(recordDefault = false)
      coroutineScope.launch {
        delay(1.minutes)

        if (!FeatureUsageLogger.isEnabled()) {
          channel.close()
          return@launch
        }
        for (request in channel) {
          synchronized(printer) {
            logRequest(request, printer)
          }
        }
      }

      (project ?: ApplicationManager.getApplication()).messageBus.simpleConnect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          if (!FeatureUsageLogger.isEnabled()) return
          // process all pending requests
          synchronized(printer) {
            while (true) {
              val request = channel.tryReceive().getOrNull() ?: break
              logRequest(request, printer)
            }
          }
        }
      })
    }
  }

  private fun logRequest(request: LogRequest,
                        printer: FeatureUsageSettingsEventPrinter) {
    when (request) {
      is LogConfigurationState -> {
        printer.logConfigurationState(request.componentName, request.state, project)
      }
      is LogConfigurationStateChanged -> {
        printer.logConfigurationStateChanged(request.componentName, request.state, project)
      }
      is LogDefaultConfigurationState -> {
        printer.logDefaultConfigurationState(request.componentName, request.aClass, project)
      }
    }
  }

  fun logDefaultConfigurationState(componentName: String, aClass: Class<*>) {
    channel.trySend(LogDefaultConfigurationState(componentName = componentName, aClass = aClass))
  }

  fun logConfigurationState(componentName: String, state: Any) {
    channel.trySend(LogConfigurationState(componentName = componentName, state = state))
  }

  fun logConfigurationChanged(componentName: String, state: Any) {
    channel.trySend(LogConfigurationStateChanged(componentName = componentName, state = state))
  }
}

private val counter = AtomicInteger(0)

open class FeatureUsageSettingsEventPrinter(private val recordDefault: Boolean) {
  private val valuesExtractor = ConfigurationStateExtractor(recordDefault)

  fun logDefaultConfigurationState(componentName: String, clazz: Class<*>, project: Project?) {
    try {
      if (recordDefault) {
        val default = jdomSerializer.getDefaultSerializationFilter().getDefaultValue(clazz)
        logConfigurationState(componentName, default, project)
      }
      else if (clazz != Element::class.java) {
        val pluginInfo = getPluginInfo(clazz)
        if (pluginInfo.isDevelopedByJetBrains()) {
          recordedComponents.add(componentName)
          logConfig(group = GROUP,
                    eventId = "invoked",
                    data = createComponentData(project, componentName, pluginInfo),
                    id = counter.incrementAndGet())
        }
      }
    }
    catch (e: Exception) {
      logger<FeatureUsageSettingsEventPrinter>().warn("Cannot initialize default settings for '$componentName'")
    }
  }

  fun logConfigurationStateChanged(componentName: String, state: Any, project: Project?) {
    val (optionsValues, pluginInfo) = valuesExtractor.extract(project, componentName, state) ?: return
    val id = counter.incrementAndGet()
    for (data in optionsValues) {
      logSettingsChanged("component_changed_option", data, id)
    }

    if (!recordDefault) {
      logSettingsChanged("component_changed", createComponentData(project, componentName, pluginInfo), id)
    }
  }

  fun logConfigurationState(componentName: String, state: Any, project: Project?) {
    val (optionsValues, pluginInfo) = valuesExtractor.extract(project, componentName, state) ?: return
    val eventId = if (recordDefault) "option" else "not.default"
    val id = counter.incrementAndGet()
    for (data in optionsValues) {
      logConfig(group = GROUP, eventId = eventId, data = data, id = id)
    }

    if (!recordDefault) {
      logConfig(group = GROUP, eventId = "invoked", data = createComponentData(project, componentName, pluginInfo), id = id)
    }
  }

  protected open fun logConfig(group: EventLogGroup, @NonNls eventId: String, data: FeatureUsageData, id: Int) {
    FeatureUsageLogger.logState(group, eventId, data.addData(ID_FIELD, id).build())
  }

  protected open fun logSettingsChanged(@NonNls eventId: String, data: FeatureUsageData, id: Int) {
    FUCounterUsageLogger.getInstance().logEvent(CHANGES_GROUP, eventId, data.addData(ID_FIELD, id))
  }
}

private fun createComponentData(project: Project?, componentName: String, pluginInfo: PluginInfo): FeatureUsageData {
  val data = FeatureUsageData("FUS")
    .addData("component", componentName)
    .addPluginInfo(pluginInfo)
  if (project?.isDefault == true) {
    data.addData("default_project", true)
  }
  else {
    data.addProject(project)
  }
  return data
}

internal data class ConfigurationState(@JvmField val optionsValues: List<FeatureUsageData>, @JvmField val pluginInfo: PluginInfo)

internal data class ConfigurationStateExtractor(private val recordDefault: Boolean) {
  internal fun extract(project: Project?, componentName: String, state: Any): ConfigurationState? {
    if (state is Element || state is JDOMExternalizable) {
      return null
    }

    val pluginInfo = getPluginInfo(state.javaClass)
    if (!pluginInfo.isDevelopedByJetBrains()) {
      return null
    }

    val accessors = BeanBinding.getAccessors(state.javaClass)
    if (accessors.isEmpty()) {
      return null
    }

    recordedComponents.add(componentName)
    val optionsValues = accessors.mapNotNull { extractOptionValue(project, it, state, componentName, pluginInfo) }
    return ConfigurationState(optionsValues, pluginInfo)
  }

  private fun extractOptionValue(project: Project?,
                                 accessor: Accessor,
                                 state: Any,
                                 componentName: String,
                                 pluginInfo: PluginInfo): FeatureUsageData? {
    if (accessor.getAnnotation(SkipReportingStatistics::class.java) != null) {
      return null
    }

    val type = accessor.genericType
    return when {
      type === Boolean::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "bool") ?: return null
        (accessor.readUnsafe(state) as? Boolean)?.let { data.addData("value", it) }
        data
      }
      type === Int::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "int") ?: return null
        readValue<Int>(accessor, state)?.let { data.addData("value", it) }
        data
      }
      type === Long::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "int")?: return null
        readValue<Long>(accessor, state)?.let { data.addData("value", it) }
        data
      }
      type === Float::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "float")?: return null
        readValue<Float>(accessor, state)?.let { data.addData("value", it) }
        data
      }
      type === Double::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "float")?: return null
        readValue<Double>(accessor, state)?.let { data.addData("value", it) }
        data
      }
      type is Class<*> && type.isEnum -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "enum")?: return null
        readValue(accessor, state) { (it as? Enum<*>)?.name }?.let { data.addData("value", it) }
        data
      }
      type == String::class.java -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, "string") ?: return null
        val value = readValue(accessor, state) { value ->
          if (value is String && value in accessor.getAnnotation(ReportValue::class.java).possibleValues) {
            value
          }
          else null
        }
        value?.let { data.addData("value", it) }
        data
      }
      else -> null
    }
  }

  private fun createOptionData(project: Project?,
                               componentName: String,
                               pluginInfo: PluginInfo,
                               accessor: Accessor,
                               state: Any,
                               @NonNls type: String): FeatureUsageData? {
    val isDefault = !jdomSerializer.getDefaultSerializationFilter().accepts(accessor, state)
    if (isDefault && !recordDefault) {
      return null
    }

    val data = createComponentData(project = project, componentName = componentName, pluginInfo = pluginInfo)
    data.addData("type", type)
    data.addData("name", accessor.name)
    recordedOptionNames.add(accessor.name)
    if (recordDefault) {
      data.addData("default", isDefault)
    }
    return data
  }

  private inline fun <reified T> readValue(accessor: Accessor, state: Any, noinline transformValue: ((Any?) -> T?)? = null): T? {
    if (accessor.getAnnotation(ReportValue::class.java) != null) {
      val value = accessor.readUnsafe(state)
      return if (transformValue != null) {
        transformValue(value)
      }
      else {
        value as? T
      }
    }
    return null
  }

}
