// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.configurationStore.jdomSerializer
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SkipReportingStatistics
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.getBeanAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

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
    } else {
      val printer = FeatureUsageSettingsEventPrinter(recordDefault = false)
      coroutineScope.launch {
        delay(1.minutes)

        if (!FeatureUsageLogger.getInstance().isEnabled()) {
          channel.close()
          return@launch
        }
        for (request in channel) {
          synchronized(printer) {
            logRequest(request, printer)
          }
        }
      }

      val messageBus = project?.messageBus ?: ApplicationManager.getApplication().messageBus
      messageBus.simpleConnect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          if (!FeatureUsageLogger.getInstance().isEnabled()) return
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

@ApiStatus.Internal
open class FeatureUsageSettingsEventPrinter(private val recordDefault: Boolean) {
  private val valuesExtractor = ConfigurationStateExtractor(recordDefault)

  fun logDefaultConfigurationState(componentName: String, clazz: Class<*>, project: Project?) {
    try {
      if (recordDefault) {
        val default = jdomSerializer.getDefaultSerializationFilter().getDefaultValue(clazz)
        logConfigurationState(componentName, default, project)
      } else if (clazz != Element::class.java) {
        val pluginInfo = getPluginInfo(clazz)
        if (pluginInfo.isDevelopedByJetBrains()) {
          recordedComponents.add(componentName)
          val data = createComponentData(project, componentName, pluginInfo)
          logConfig(SettingsCollector::logInvoked, project, data, counter.incrementAndGet())
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
      logSettingsChanged(SettingsChangesCollector::logComponentChangedOption, project, data, id)
    }
    if (!recordDefault) {
      logSettingsChanged(
        SettingsChangesCollector::logComponentChanged,
        project,
        createComponentData(project, componentName, pluginInfo),
        id)
    }
  }

  fun logConfigurationState(componentName: String, state: Any, project: Project?) {
    val (optionsValues, pluginInfo) = valuesExtractor.extract(project, componentName, state) ?: return
    val eventId = if (recordDefault) SettingsCollector::logOption else SettingsCollector::logNotDefault
    val id = counter.incrementAndGet()
    for (data in optionsValues) {
      logConfig(eventId, project, data, id)
    }
    if (!recordDefault) {
      logConfig(SettingsCollector::logInvoked, project, createComponentData(project, componentName, pluginInfo), id)
    }
  }

  protected open fun logConfig(@NonNls eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                               project: Project?,
                               data: MutableList<EventPair<*>>,
                               id: Int) {
    data.add(SettingsFields.ID_FIELD.with(id))
    eventFunction(project, data)
  }

  protected open fun logSettingsChanged(@NonNls eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                                        project: Project?,
                                        data: MutableList<EventPair<*>>,
                                        id: Int) {
    data.add(SettingsFields.ID_FIELD.with(id))
    eventFunction(project, data)
  }
}

private fun createComponentData(project: Project?, componentName: String, pluginInfo: PluginInfo): MutableList<EventPair<*>> {
  val data: MutableList<EventPair<*>> = mutableListOf()
    data.add(SettingsFields.COMPONENT_FIELD.with(componentName))
    data.add(SettingsFields.PLUGIN_INFO_FIELD.with(pluginInfo))
  if (project?.isDefault == true) {
    data.add(SettingsFields.DEFAULT_PROJECT_FIELD.with( true))
  }
  return data
}

internal data class ConfigurationState(@JvmField var optionsValues: List<MutableList<EventPair<*>>>, @JvmField val pluginInfo: PluginInfo)

internal class ConfigurationStateExtractor(private val recordDefault: Boolean) {
  internal fun extract(project: Project?, componentName: String, state: Any): ConfigurationState? {
    @Suppress("DEPRECATION")
    if (state is Element || state is com.intellij.openapi.util.JDOMExternalizable) {
      return null
    }

    val pluginInfo = getPluginInfo(state.javaClass)
    if (!pluginInfo.isDevelopedByJetBrains()) {
      return null
    }

    val accessors = getBeanAccessors(state.javaClass)
    if (accessors.isEmpty()) {
      return null
    }

    recordedComponents.add(componentName)
    val optionsValues = accessors.mapNotNull {
      extractOptionValue(project = project, accessor = it, state = state, componentName = componentName, pluginInfo = pluginInfo)
    }
    return ConfigurationState(optionsValues, pluginInfo)
  }

  private fun extractOptionValue(project: Project?,
                                 accessor: Accessor,
                                 state: Any,
                                 componentName: String,
                                 pluginInfo: PluginInfo): MutableList<EventPair<*>>? {
    if (accessor.getAnnotation(SkipReportingStatistics::class.java) != null) {
      return null
    }

    val type = accessor.genericType
    return when {
      type === Boolean::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.BOOl) ?: return null
        (accessor.readUnsafe(state) as? Boolean)?.let { data.add(SettingsFields.VALUE_FIELD.with(it.toString())) }
        data
      }
      type === Int::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.INT) ?: return null
        readValue<Int>(accessor, state)?.let { data.add(SettingsFields.VALUE_FIELD.with(it.toString())) }
        data
      }
      type === Long::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.INT) ?: return null
        readValue<Long>(accessor, state)?.let { data.add(SettingsFields.VALUE_FIELD.with(it.toString())) }
        data
      }
      type === Float::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.FLOAT)
                   ?: return null
        readValue<Float>(accessor, state)?.let { data.add(SettingsFields.VALUE_FIELD.with(it.toString())) }
        data
      }
      type === Double::class.javaPrimitiveType -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.FLOAT)
                   ?: return null
        readValue<Double>(accessor, state)?.let { data.add(SettingsFields.VALUE_FIELD.with(it.toString())) }
        data
      }
      type is Class<*> && type.isEnum -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.ENUM) ?: return null
        readValue(accessor, state) { (it as? Enum<*>)?.name }?.let { data.add(SettingsFields.VALUE_FIELD.with(it)) }
        data
      }
      type == String::class.java -> {
        val data = createOptionData(project, componentName, pluginInfo, accessor, state, SettingsFields.Companion.Types.STRING)
                   ?: return null
        val value = readValue(accessor, state) { value ->
          if (value is String && value in accessor.getAnnotation(ReportValue::class.java).possibleValues) {
            value
          }
          else null
        }
        value?.let { data.add(SettingsFields.VALUE_FIELD.with(it)) }
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
                               @NonNls type: SettingsFields.Companion.Types): MutableList<EventPair<*>>? {
    val isDefault = !jdomSerializer.getDefaultSerializationFilter().accepts(accessor, state)
    if (isDefault && !recordDefault) {
      return null
    }

    val data = createComponentData(project = project, componentName = componentName, pluginInfo = pluginInfo)
    data.add(SettingsFields.TYPE_FIELD.with(type))
    data.add(SettingsFields.NAME_FIELD.with(accessor.name))
    recordedOptionNames.add(accessor.name)
    if (recordDefault) {
      data.add(SettingsFields.DEFAULT_FIELD.with(isDefault))
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
