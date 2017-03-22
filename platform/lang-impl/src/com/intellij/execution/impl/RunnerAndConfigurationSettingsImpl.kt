/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl

import com.intellij.configurationStore.SerializableScheme
import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.configurationStore.serializeInto
import com.intellij.execution.*
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionException
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element

private val LOG = Logger.getInstance("#com.intellij.execution.impl.RunnerAndConfigurationSettings")

private val RUNNER_ID = "RunnerId"

private val CONFIGURATION_TYPE_ATTRIBUTE = "type"
private val FACTORY_NAME_ATTRIBUTE = "factoryName"
private val FOLDER_NAME = "folderName"
val NAME_ATTR = "name"
val DUMMY_ELEMENT_NAME = "dummy"
private val TEMPORARY_ATTRIBUTE = "temporary"
private val EDIT_BEFORE_RUN = "editBeforeRun"
private val ACTIVATE_TOOLWINDOW_BEFORE_RUN = "activateToolWindowBeforeRun"

private val TEMP_CONFIGURATION = "tempConfiguration"

class RunnerAndConfigurationSettingsImpl : Cloneable, RunnerAndConfigurationSettings, Comparable<Any>, RunConfigurationScheme, SerializableScheme {
  companion object {
    @JvmField
    val SINGLETON = "singleton"

    @JvmField
    internal val TEMPLATE_FLAG_ATTRIBUTE = "default"
  }

  private val manager: RunManagerImpl
  private var configuration: RunConfiguration? = null
  private var isTemplate = false

  private val runnerSettings = object : RunnerItem<RunnerSettings>("RunnerSettings") {
    override fun createSettings(runner: ProgramRunner<*>) = runner.createConfigurationData(InfoProvider(runner))
  }

  private val configurationPerRunnerSettings = object : RunnerItem<ConfigurationPerRunnerSettings>("ConfigurationWrapper") {
    override fun createSettings(runner: ProgramRunner<*>) = configuration!!.createRunnerSettings(InfoProvider(runner))
  }

  private var isTemporary: Boolean = false
  private var isEditBeforeRun: Boolean = false
  private var isActivateToolWindowBeforeRun = true
  private var singleton: Boolean = false
  private var wasSingletonSpecifiedExplicitly: Boolean = false
  private var folderName: String? = null

  constructor(manager: RunManagerImpl) {
    this.manager = manager
  }

  @JvmOverloads
  constructor(manager: RunManagerImpl, configuration: RunConfiguration, isTemplate: Boolean = false) {
    this.manager = manager
    this.configuration = configuration
    this.isTemplate = isTemplate
  }

  override fun getFactory() = configuration?.factory

  override fun isTemplate() = isTemplate

  override fun isTemporary() = isTemporary

  override fun setTemporary(temporary: Boolean) {
    isTemporary = temporary
  }

  override fun getConfiguration(): RunConfiguration = configuration!!

  override fun createFactory() = Factory<RunnerAndConfigurationSettings> {
    val configuration = configuration!!
    RunnerAndConfigurationSettingsImpl(manager, configuration.factory.createConfiguration(ExecutionBundle.message("default.run.configuration.name"), configuration), false)
  }

  override fun setName(name: String) {
    configuration!!.name = name
  }

  override fun getName(): String {
    val configuration = configuration!!
    if (isTemplate) {
      return "<template> of ${configuration.factory.name}"
    }
    return configuration.name
  }

  override fun getUniqueID(): String {
    val configuration = configuration!!
    return "${configuration.type.displayName}.${configuration.name}${(configuration as? UnknownRunConfiguration)?.uniqueID ?: ""}"
  }

  override fun setEditBeforeRun(b: Boolean) {
    isEditBeforeRun = b
  }

  override fun isEditBeforeRun() = isEditBeforeRun

  override fun setActivateToolWindowBeforeRun(activate: Boolean) {
    isActivateToolWindowBeforeRun = activate
  }

  override fun isActivateToolWindowBeforeRun() = isActivateToolWindowBeforeRun

  override fun setSingleton(singleton: Boolean) {
    this.singleton = singleton
  }

  override fun isSingleton() = singleton

  override fun setFolderName(folderName: String?) {
    this.folderName = folderName
  }

  override fun getFolderName() = folderName

  private fun getFactory(element: Element): ConfigurationFactory? {
    val typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE)
    val factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE)
    return manager.getFactory(typeName, factoryName, !isTemplate)
  }

  fun readExternal(element: Element) {
    isTemplate = element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE).toBoolean()
    isTemporary = element.getAttributeValue(TEMPORARY_ATTRIBUTE).toBoolean() || TEMP_CONFIGURATION == element.name
    isEditBeforeRun = (element.getAttributeValue(EDIT_BEFORE_RUN)).toBoolean()
    val value = element.getAttributeValue(ACTIVATE_TOOLWINDOW_BEFORE_RUN)
    isActivateToolWindowBeforeRun = value == null || value.toBoolean()
    folderName = element.getAttributeValue(FOLDER_NAME)
    val factory = getFactory(element) ?: return

    wasSingletonSpecifiedExplicitly = false
    if (isTemplate) {
      singleton = factory.isConfigurationSingletonByDefault
    }
    else {
      val singletonStr = element.getAttributeValue(SINGLETON)
      if (StringUtil.isEmpty(singletonStr)) {
        singleton = factory.isConfigurationSingletonByDefault
      }
      else {
        wasSingletonSpecifiedExplicitly = true
        singleton = singletonStr.toBoolean()
      }
    }

    val configuration = if (isTemplate) {
      manager.getConfigurationTemplate(factory).configuration
    }
    else {
      // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue.
      manager.doCreateConfiguration(element.getAttributeValue(NAME_ATTR), factory, false)
    }

    this.configuration = configuration

    PathMacroManager.getInstance(configuration.project).expandPaths(element)
    if (configuration is ModuleBasedConfiguration<*>) {
      configuration.configurationModule.module?.let {
        PathMacroManager.getInstance(it).expandPaths(element)
      }
    }

    if (configuration is PersistentStateComponent<*>) {
      configuration.deserializeAndLoadState(element)
    }
    else {
      configuration.readExternal(element)
    }

    runnerSettings.loadState(element)
    configurationPerRunnerSettings.loadState(element)
  }

  fun writeExternal(element: Element) {
    val configuration = configuration
    val factory = configuration!!.factory
    if (configuration !is UnknownRunConfiguration) {
      if (isTemplate) {
        element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, "true")
      }
      else {
        element.setAttribute(NAME_ATTR, configuration.name)
      }

      element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.type.id)
      element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.name)
      if (folderName != null) {
        element.setAttribute(FOLDER_NAME, folderName!!)
      }

      if (isEditBeforeRun) {
        element.setAttribute(EDIT_BEFORE_RUN, "true")
      }
      if (!isActivateToolWindowBeforeRun) {
        element.setAttribute(ACTIVATE_TOOLWINDOW_BEFORE_RUN, "false")
      }
      if (wasSingletonSpecifiedExplicitly || singleton != factory.isConfigurationSingletonByDefault) {
        element.setAttribute(SINGLETON, singleton.toString())
      }
      if (isTemporary) {
        element.setAttribute(TEMPORARY_ATTRIBUTE, "true")
      }
    }

    serializeConfigurationInto(configuration, element)

    if (configuration !is UnknownRunConfiguration) {
      runnerSettings.getState(element)
      configurationPerRunnerSettings.getState(element)
    }
  }

  private fun serializeConfigurationInto(configuration: RunConfiguration, element: Element) {
    if (configuration is PersistentStateComponent<*>) {
      configuration.state!!.serializeInto(element)
    }
    else {
      configuration.writeExternal(element)
    }
  }

  override fun writeScheme(): Element {
    val element = Element("configuration")
    writeExternal(element)

    if (configuration !is UnknownRunConfiguration) {
      manager.doWriteConfiguration(this, element)
    }

    return element
  }

  override fun checkSettings(executor: Executor?) {
    val configuration = configuration!!
    configuration.checkConfiguration()
    if (configuration !is RunConfigurationBase) {
      return
    }

    val runners = THashSet<ProgramRunner<*>>()
    runners.addAll(runnerSettings.settings.keys)
    runners.addAll(configurationPerRunnerSettings.settings.keys)
    for (runner in runners) {
      if (executor == null || runner.canRun(executor.id, configuration)) {
        configuration.checkRunnerSettings(runner, runnerSettings.settings[runner],
          configurationPerRunnerSettings.settings[runner])
      }
    }
    if (executor != null) {
      configuration.checkSettingsBeforeRun()
    }
  }

  override fun canRunOn(target: ExecutionTarget): Boolean {
    val configuration = configuration
    return if (configuration is TargetAwareRunProfile) configuration.canRunOn(target) else true
  }

  override fun getRunnerSettings(runner: ProgramRunner<*>) = runnerSettings.getOrCreateSettings(runner)

  override fun getConfigurationSettings(runner: ProgramRunner<*>) = configurationPerRunnerSettings.getOrCreateSettings(runner)

  override fun getType() = configuration?.type

  public override fun clone(): RunnerAndConfigurationSettings {
    val copy = RunnerAndConfigurationSettingsImpl(manager, configuration!!.clone(), false)
    copy.importRunnerAndConfigurationSettings(this)
    return copy
  }

  fun importRunnerAndConfigurationSettings(template: RunnerAndConfigurationSettingsImpl) {
    importFromTemplate(template.runnerSettings, runnerSettings)
    importFromTemplate(template.configurationPerRunnerSettings, configurationPerRunnerSettings)

    isSingleton = template.isSingleton
    isEditBeforeRun = template.isEditBeforeRun
    isActivateToolWindowBeforeRun = template.isActivateToolWindowBeforeRun
  }

  private fun <T> importFromTemplate(templateItem: RunnerItem<T>, item: RunnerItem<T>) {
    for (runner in templateItem.settings.keys) {
      val data = item.createSettings(runner)
      item.settings.put(runner, data)
      if (data == null) {
        continue
      }

      val temp = Element(DUMMY_ELEMENT_NAME)
      val templateSettings = templateItem.settings.get(runner) ?: continue
      try {
        @Suppress("DEPRECATION")
        (templateSettings as JDOMExternalizable).writeExternal(temp)
        @Suppress("DEPRECATION")
        (data as JDOMExternalizable).readExternal(temp)
      }
      catch (e: WriteExternalException) {
        LOG.error(e)
      }
      catch (e: InvalidDataException) {
        LOG.error(e)
      }
    }
  }

  override fun compareTo(other: Any) = if (other is RunnerAndConfigurationSettings) name.compareTo(other.name) else 0

  override fun toString(): String {
    val type = type
    return "${if (type == null) "" else "${type.displayName}: "}${if (isTemplate) "<template>" else name}"
  }

  private inner class InfoProvider(override val runner: ProgramRunner<*>) : ConfigurationInfoProvider {
    override val configuration: RunConfiguration
      get() = RunnerAndConfigurationSettingsImpl@this.configuration

    override val runnerSettings: RunnerSettings
      get() = this@RunnerAndConfigurationSettingsImpl.getRunnerSettings(runner)

    override val configurationSettings: ConfigurationPerRunnerSettings
      get() = this@RunnerAndConfigurationSettingsImpl.getConfigurationSettings(runner)
  }

  override fun getSchemeState(): SchemeState? {
    val configuration = configuration
    if (configuration is UnknownRunConfiguration) {
      return if (configuration.isDoNotStore) SchemeState.NON_PERSISTENT else SchemeState.UNCHANGED
    }
    
    if (isTemplate && configuration != null) {
      val templateConfiguration = configuration.factory.createTemplateConfiguration(manager.myProject, manager)

      val templateState = Element("state")
      serializeConfigurationInto(templateConfiguration, templateState)

      val state = writeScheme()
      if (JDOMUtil.areElementsEqual(state, templateState)) {
        return SchemeState.NON_PERSISTENT
      }
    }
    return null
  }

  private abstract inner class RunnerItem<T>(private val childTagName: String) {
    val settings = THashMap<ProgramRunner<*>, T>()

    private var unloadedSettings: MutableList<Element>? = null
    // to avoid changed files
    private val loadedIds = THashSet<String>()

    fun loadState(element: Element) {
      settings.clear()
      if (unloadedSettings != null) {
        unloadedSettings!!.clear()
      }
      loadedIds.clear()

      val iterator = element.getChildren(childTagName).iterator()
      while (iterator.hasNext()) {
        val state = iterator.next()
        val runner = findRunner(state.getAttributeValue(RUNNER_ID))
        if (runner == null) {
          iterator.remove()
        }
        add(state, runner, if (runner == null) null else createSettings(runner))
      }
    }

    private fun findRunner(runnerId: String): ProgramRunner<*>? {
      val runnersById = ProgramRunner.PROGRAM_RUNNER_EP.extensions.filter { runnerId == it.runnerId }
      return if (runnersById.isEmpty()) {
        null
      }
      else if (runnersById.size == 1) {
        runnersById.firstOrNull()
      }
      else {
        LOG.error("More than one runner found for ID: $runnerId")
        for (executor in ExecutorRegistry.getInstance().registeredExecutors) {
          runnersById.firstOrNull { it.canRun(executor.id, configuration!!)  }?.let {
            return it
          }
        }
        null
      }
    }

    fun getState(element: Element) {
      val runnerSettings = SmartList<Element>()
      for (runner in settings.keys) {
        val settings = this.settings.get(runner)
        val wasLoaded = loadedIds.contains(runner.runnerId)
        if (settings == null && !wasLoaded) {
          continue
        }

        val state = Element(childTagName)
        if (settings != null) {
          @Suppress("DEPRECATION")
          (settings as JDOMExternalizable).writeExternal(state)
        }
        if (wasLoaded || !JDOMUtil.isEmpty(state)) {
          state.setAttribute(RUNNER_ID, runner.runnerId)
          runnerSettings.add(state)
        }
      }
      if (unloadedSettings != null) {
        for (unloadedSetting in unloadedSettings!!) {
          runnerSettings.add(unloadedSetting.clone())
        }
      }
      runnerSettings.sort { o1, o2 ->
        val attributeValue1 = o1.getAttributeValue(RUNNER_ID)
        if (attributeValue1 == null) 1 else StringUtil.compare(attributeValue1, o2.getAttributeValue(RUNNER_ID), false)
      }
      for (runnerSetting in runnerSettings) {
        element.addContent(runnerSetting)
      }
    }

    abstract fun createSettings(runner: ProgramRunner<*>): T?

    private fun add(state: Element, runner: ProgramRunner<*>?, data: T?) {
      if (runner == null) {
        if (unloadedSettings == null) {
          unloadedSettings = SmartList<Element>()
        }
        unloadedSettings!!.add(state)
        return
      }

      if (data != null) {
        @Suppress("DEPRECATION")
        (data as JDOMExternalizable).readExternal(state)
      }

      settings.put(runner, data)
      loadedIds.add(runner.runnerId)
    }

    fun getOrCreateSettings(runner: ProgramRunner<*>): T {
      var result: T? = settings[runner]
      if (result == null) {
        try {
          result = createSettings(runner)
          settings.put(runner, result)
        }
        catch (ignored: AbstractMethodError) {
          LOG.error("Update failed for: ${configuration!!.type.displayName}, runner: ${runner.runnerId}", ExtensionException(runner.javaClass))
        }

      }
      return result!!
    }
  }
}
