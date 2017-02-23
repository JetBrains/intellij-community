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
internal val TEMPLATE_FLAG_ATTRIBUTE = "default"
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
  }

  private val manager: RunManagerImpl
  private var myConfiguration: RunConfiguration? = null
  private var myIsTemplate: Boolean = false

  private val myRunnerSettings = object : RunnerItem<RunnerSettings>("RunnerSettings") {
    override fun createSettings(runner: ProgramRunner<*>) = runner.createConfigurationData(InfoProvider(runner))
  }

  private val myConfigurationPerRunnerSettings = object : RunnerItem<ConfigurationPerRunnerSettings>("ConfigurationWrapper") {
    override fun createSettings(runner: ProgramRunner<*>) = myConfiguration!!.createRunnerSettings(InfoProvider(runner))
  }

  private var myTemporary: Boolean = false
  private var myEditBeforeRun: Boolean = false
  private var myActivateToolWindowBeforeRun = true
  private var mySingleton: Boolean = false
  private var myWasSingletonSpecifiedExplicitly: Boolean = false
  private var myFolderName: String? = null

  constructor(manager: RunManagerImpl) {
    this.manager = manager
  }

  constructor(manager: RunManagerImpl, configuration: RunConfiguration, isTemplate: Boolean) {
    this.manager = manager
    myConfiguration = configuration
    myIsTemplate = isTemplate
  }

  override fun getFactory() = myConfiguration?.factory

  override fun isTemplate() = myIsTemplate

  override fun isTemporary() = myTemporary

  override fun setTemporary(temporary: Boolean) {
    myTemporary = temporary
  }

  override fun getConfiguration(): RunConfiguration = myConfiguration!!

  override fun createFactory() = Factory<RunnerAndConfigurationSettings> {
    val configuration = myConfiguration!!
    RunnerAndConfigurationSettingsImpl(manager, configuration.factory.createConfiguration(ExecutionBundle.message("default.run.configuration.name"), configuration), false)
  }

  override fun setName(name: String) {
    myConfiguration!!.name = name
  }

  override fun getName() = myConfiguration!!.name

  override fun getUniqueID(): String {
    val configuration = myConfiguration!!
    return "${configuration.type.displayName}.${configuration.name}${(configuration as? UnknownRunConfiguration)?.uniqueID ?: ""}"
  }

  override fun setEditBeforeRun(b: Boolean) {
    myEditBeforeRun = b
  }

  override fun isEditBeforeRun() = myEditBeforeRun

  override fun setActivateToolWindowBeforeRun(activate: Boolean) {
    myActivateToolWindowBeforeRun = activate
  }

  override fun isActivateToolWindowBeforeRun() = myActivateToolWindowBeforeRun

  override fun setSingleton(singleton: Boolean) {
    mySingleton = singleton
  }

  override fun isSingleton() = mySingleton

  override fun setFolderName(folderName: String?) {
    myFolderName = folderName
  }

  override fun getFolderName() = myFolderName

  private fun getFactory(element: Element): ConfigurationFactory? {
    val typeName = element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE)
    val factoryName = element.getAttributeValue(FACTORY_NAME_ATTRIBUTE)
    return manager.getFactory(typeName, factoryName, !myIsTemplate)
  }

  fun readExternal(element: Element) {
    myIsTemplate = java.lang.Boolean.parseBoolean(element.getAttributeValue(TEMPLATE_FLAG_ATTRIBUTE))
    myTemporary = java.lang.Boolean.parseBoolean(element.getAttributeValue(TEMPORARY_ATTRIBUTE)) || TEMP_CONFIGURATION == element.name
    myEditBeforeRun = java.lang.Boolean.parseBoolean(element.getAttributeValue(EDIT_BEFORE_RUN))
    val value = element.getAttributeValue(ACTIVATE_TOOLWINDOW_BEFORE_RUN)
    myActivateToolWindowBeforeRun = value == null || java.lang.Boolean.parseBoolean(value)
    myFolderName = element.getAttributeValue(FOLDER_NAME)
    val factory = getFactory(element) ?: return

    myWasSingletonSpecifiedExplicitly = false
    if (myIsTemplate) {
      mySingleton = factory.isConfigurationSingletonByDefault
    }
    else {
      val singletonStr = element.getAttributeValue(SINGLETON)
      if (StringUtil.isEmpty(singletonStr)) {
        mySingleton = factory.isConfigurationSingletonByDefault
      }
      else {
        myWasSingletonSpecifiedExplicitly = true
        mySingleton = java.lang.Boolean.parseBoolean(singletonStr)
      }
    }

    myConfiguration = if (myIsTemplate) {
      manager.getConfigurationTemplate(factory).configuration
    }
    else {
      // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue.
      manager.doCreateConfiguration(element.getAttributeValue(NAME_ATTR), factory, false)
    }

    PathMacroManager.getInstance(myConfiguration!!.project).expandPaths(element)
    if (myConfiguration is ModuleBasedConfiguration<*>) {
      (myConfiguration as ModuleBasedConfiguration<*>).configurationModule.module?.let {
        PathMacroManager.getInstance(it).expandPaths(element)
      }
    }

    if (myConfiguration is PersistentStateComponent<*>) {
      (myConfiguration as PersistentStateComponent<*>).deserializeAndLoadState(element)
    }
    else {
      myConfiguration!!.readExternal(element)
    }

    myRunnerSettings.loadState(element)
    myConfigurationPerRunnerSettings.loadState(element)
  }

  fun writeExternal(element: Element) {
    val configuration = myConfiguration
    val factory = configuration!!.factory
    if (configuration !is UnknownRunConfiguration) {
      if (myIsTemplate) {
        element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, "true")
      }
      else {
        element.setAttribute(NAME_ATTR, configuration.name)
      }

      element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.type.id)
      element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.name)
      if (myFolderName != null) {
        element.setAttribute(FOLDER_NAME, myFolderName!!)
      }

      if (isEditBeforeRun) {
        element.setAttribute(EDIT_BEFORE_RUN, "true")
      }
      if (!isActivateToolWindowBeforeRun) {
        element.setAttribute(ACTIVATE_TOOLWINDOW_BEFORE_RUN, "false")
      }
      if (myWasSingletonSpecifiedExplicitly || mySingleton != factory.isConfigurationSingletonByDefault) {
        element.setAttribute(SINGLETON, mySingleton.toString())
      }
      if (myTemporary) {
        element.setAttribute(TEMPORARY_ATTRIBUTE, "true")
      }
    }

    if (configuration is PersistentStateComponent<*>) {
      configuration.state!!.serializeInto<Any>(element)
    }
    else {
      configuration.writeExternal(element)
    }

    if (configuration !is UnknownRunConfiguration) {
      myRunnerSettings.getState(element)
      myConfigurationPerRunnerSettings.getState(element)
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
    val configuration = myConfiguration!!
    configuration.checkConfiguration()
    if (configuration !is RunConfigurationBase) {
      return
    }

    val runners = THashSet<ProgramRunner<*>>()
    runners.addAll(myRunnerSettings.settings.keys)
    runners.addAll(myConfigurationPerRunnerSettings.settings.keys)
    for (runner in runners) {
      if (executor == null || runner.canRun(executor.id, configuration)) {
        configuration.checkRunnerSettings(runner, myRunnerSettings.settings[runner],
          myConfigurationPerRunnerSettings.settings[runner])
      }
    }
    if (executor != null) {
      configuration.checkSettingsBeforeRun()
    }
  }

  override fun canRunOn(target: ExecutionTarget): Boolean {
    val configuration = myConfiguration
    return if (configuration is TargetAwareRunProfile) configuration.canRunOn(target) else true
  }

  override fun getRunnerSettings(runner: ProgramRunner<*>) = myRunnerSettings.getOrCreateSettings(runner)

  override fun getConfigurationSettings(runner: ProgramRunner<*>) = myConfigurationPerRunnerSettings.getOrCreateSettings(runner)

  override fun getType() = myConfiguration?.type

  public override fun clone(): RunnerAndConfigurationSettings {
    val copy = RunnerAndConfigurationSettingsImpl(manager, myConfiguration!!.clone(), false)
    copy.importRunnerAndConfigurationSettings(this)
    return copy
  }

  fun importRunnerAndConfigurationSettings(template: RunnerAndConfigurationSettingsImpl) {
    importFromTemplate(template.myRunnerSettings, myRunnerSettings)
    importFromTemplate(template.myConfigurationPerRunnerSettings, myConfigurationPerRunnerSettings)

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

  private inner class InfoProvider(private val runner: ProgramRunner<*>) : ConfigurationInfoProvider {
    override fun getRunner() = runner

    override fun getConfiguration() = myConfiguration

    override fun getRunnerSettings() = this@RunnerAndConfigurationSettingsImpl.getRunnerSettings(runner)

    override fun getConfigurationSettings() = this@RunnerAndConfigurationSettingsImpl.getConfigurationSettings(runner)
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
          runnersById.firstOrNull { it.canRun(executor.id, myConfiguration!!)  }?.let {
            return it
          }
        }
        null
      }
    }

    fun getState(element: Element) {
      val runnerSettings = SmartList<Element>()
      for (runner in settings.keys) {
        val settings = this.settings[runner]
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
          LOG.error("Update failed for: ${myConfiguration!!.type.displayName}, runner: ${runner.runnerId}", ExtensionException(runner.javaClass))
        }

      }
      return result!!
    }
  }
}
