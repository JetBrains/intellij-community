// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.configurationStore.SerializableScheme
import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.configurationStore.serializeStateInto
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.impl.BasePathMacroManager
import com.intellij.openapi.components.impl.ProjectPathMacroManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionException
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.getAttributeBooleanValue
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jdom.Element
import org.jetbrains.jps.model.serialization.PathMacroUtil
import java.util.*

private val LOG = logger<RunnerAndConfigurationSettings>()

private const val RUNNER_ID = "RunnerId"

private const val CONFIGURATION_TYPE_ATTRIBUTE = "type"
private const val FACTORY_NAME_ATTRIBUTE = "factoryName"
private const val FOLDER_NAME = "folderName"
const val NAME_ATTR: String = "name"
const val DUMMY_ELEMENT_NAME: String = "dummy"
private const val TEMPORARY_ATTRIBUTE = "temporary"
private const val EDIT_BEFORE_RUN = "editBeforeRun"
private const val ACTIVATE_TOOLWINDOW_BEFORE_RUN = "activateToolWindowBeforeRun"

private const val TEMP_CONFIGURATION = "tempConfiguration"
internal const val TEMPLATE_FLAG_ATTRIBUTE = "default"

const val SINGLETON: String = "singleton"

enum class RunConfigurationLevel {
  WORKSPACE, PROJECT, TEMPORARY
}

class RunnerAndConfigurationSettingsImpl @JvmOverloads constructor(val manager: RunManagerImpl,
                                                                   private var _configuration: RunConfiguration? = null,
                                                                   private var isTemplate: Boolean = false,
                                                                   private var isSingleton: Boolean = false,
                                                                   var level: RunConfigurationLevel = RunConfigurationLevel.WORKSPACE) : Cloneable, RunnerAndConfigurationSettings, Comparable<Any>, SerializableScheme {
  companion object {
    @JvmStatic
    fun getUniqueIdFor(configuration: RunConfiguration): String {
      if (!configuration.type.isManaged) {
        configuration.id?.let {
          return it
        }
      }
      @Suppress("DEPRECATION")
      return "${configuration.type.displayName}.${configuration.name}${(configuration as? UnknownRunConfiguration)?.uniqueID ?: ""}"
    }
  }

  private val runnerSettings = object : RunnerItem<RunnerSettings>("RunnerSettings") {
    override fun createSettings(runner: ProgramRunner<*>) = runner.createConfigurationData(InfoProvider(runner))
  }

  private val configurationPerRunnerSettings = object : RunnerItem<ConfigurationPerRunnerSettings>("ConfigurationWrapper") {
    override fun createSettings(runner: ProgramRunner<*>) = configuration.createRunnerSettings(InfoProvider(runner))
  }

  private var isEditBeforeRun = false
  private var isActivateToolWindowBeforeRun = true
  private var wasSingletonSpecifiedExplicitly = false
  private var folderName: String? = null

  private var uniqueId: String? = null

  override fun getFactory(): ConfigurationFactory = _configuration?.factory ?: UnknownConfigurationType.getFactory()

  override fun isTemplate(): Boolean = isTemplate

  override fun isTemporary(): Boolean = level == RunConfigurationLevel.TEMPORARY

  override fun setTemporary(value: Boolean) {
    level = if (value) RunConfigurationLevel.TEMPORARY else RunConfigurationLevel.WORKSPACE
  }

  override fun isShared(): Boolean = level == RunConfigurationLevel.PROJECT

  override fun setShared(value: Boolean) {
    if (value) {
      level = RunConfigurationLevel.PROJECT
    }
    else if (level == RunConfigurationLevel.PROJECT) {
      level = RunConfigurationLevel.WORKSPACE
    }
  }

  override fun getConfiguration(): RunConfiguration = _configuration ?: UnknownConfigurationType.getFactory().createTemplateConfiguration(manager.project)

  override fun createFactory(): Factory<RunnerAndConfigurationSettings> {
    return Factory {
      val configuration = configuration
      RunnerAndConfigurationSettingsImpl(manager, configuration.factory!!.createConfiguration(ExecutionBundle.message("default.run.configuration.name"), configuration))
    }
  }

  override fun setName(name: String) {
    uniqueId = null
    configuration.name = name
  }

  override fun getName(): String {
    val configuration = configuration
    if (isTemplate) {
      return "<template> of ${factory.id}"
    }
    return configuration.name
  }

  override fun getUniqueID(): String {
    var result = uniqueId
    // check name if configuration name was changed not using our setName
    if (result == null || !result.contains(configuration.name)) {
      val configuration = configuration
      @Suppress("DEPRECATION")
      result = getUniqueIdFor(configuration)
      uniqueId = result
    }
    return result
  }

  override fun setEditBeforeRun(value: Boolean) {
    isEditBeforeRun = value
  }

  override fun isEditBeforeRun(): Boolean = isEditBeforeRun

  override fun setActivateToolWindowBeforeRun(value: Boolean) {
    isActivateToolWindowBeforeRun = value
  }

  override fun isActivateToolWindowBeforeRun(): Boolean = isActivateToolWindowBeforeRun

  override fun setSingleton(value: Boolean) {
    isSingleton = value
  }

  override fun isSingleton(): Boolean = isSingleton

  override fun setFolderName(value: String?) {
    folderName = value
  }

  override fun getFolderName(): String? = folderName

  fun readExternal(element: Element, isShared: Boolean) {
    isTemplate = element.getAttributeBooleanValue(TEMPLATE_FLAG_ATTRIBUTE)

    if (isShared) {
      level = RunConfigurationLevel.PROJECT
    }
    else {
      level = if (element.getAttributeBooleanValue(TEMPORARY_ATTRIBUTE) || TEMP_CONFIGURATION == element.name) RunConfigurationLevel.TEMPORARY else RunConfigurationLevel.WORKSPACE
    }

    isEditBeforeRun = (element.getAttributeBooleanValue(EDIT_BEFORE_RUN))
    val value = element.getAttributeValue(ACTIVATE_TOOLWINDOW_BEFORE_RUN)
    @Suppress("PlatformExtensionReceiverOfInline")
    isActivateToolWindowBeforeRun = value == null || value.toBoolean()
    folderName = element.getAttributeValue(FOLDER_NAME)
    val factory = manager.getFactory(element.getAttributeValue(CONFIGURATION_TYPE_ATTRIBUTE), element.getAttributeValue(FACTORY_NAME_ATTRIBUTE), !isTemplate) ?: return

    wasSingletonSpecifiedExplicitly = false
    if (isTemplate) {
      isSingleton = factory.isConfigurationSingletonByDefault
    }
    else {
      val singletonStr = element.getAttributeValue(SINGLETON)
      if (singletonStr.isNullOrEmpty()) {
        isSingleton = factory.isConfigurationSingletonByDefault
      }
      else {
        wasSingletonSpecifiedExplicitly = true
        isSingleton = singletonStr!!.toBoolean()
      }
    }

    val configuration = if (isTemplate) {
      manager.getConfigurationTemplate(factory).configuration
    }
    else {
      // shouldn't call createConfiguration since it calls StepBeforeRunProviders that
      // may not be loaded yet. This creates initialization order issue.
      val configuration = factory.createTemplateConfiguration(manager.project, manager)
      configuration.name = element.getAttributeValue(NAME_ATTR) ?: return
      configuration
    }

    _configuration = configuration
    uniqueId = null

    PathMacroManager.getInstance(configuration.project).expandPaths(element)
    if (configuration is ModuleBasedConfiguration<*> && configuration.isModuleDirMacroSupported) {
      val moduleName = element.getChild("module")?.getAttributeValue("name")
      if (moduleName != null) {
        configuration.configurationModule.findModule(moduleName)?.let {
          PathMacroManager.getInstance(it).expandPaths(element)
        }
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

    manager.readBeforeRunTasks(element.getChild(METHOD), this, configuration)
  }

  // do not call directly
  // cannot be private - used externally
  fun writeExternal(element: Element) {
    val configuration = configuration
    if (configuration !is UnknownRunConfiguration) {
      if (isTemplate) {
        element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, "true")
      }
      else {
        if (!isNewSerializationAllowed) {
          element.setAttribute(TEMPLATE_FLAG_ATTRIBUTE, "false")
        }
        element.setAttribute(NAME_ATTR, configuration.name)
      }

      element.setAttribute(CONFIGURATION_TYPE_ATTRIBUTE, factory.type.id)
      element.setAttribute(FACTORY_NAME_ATTRIBUTE, factory.id)
      if (folderName != null) {
        element.setAttribute(FOLDER_NAME, folderName!!)
      }

      if (isEditBeforeRun) {
        element.setAttribute(EDIT_BEFORE_RUN, "true")
      }
      if (!isActivateToolWindowBeforeRun) {
        element.setAttribute(ACTIVATE_TOOLWINDOW_BEFORE_RUN, "false")
      }
      if (wasSingletonSpecifiedExplicitly || isSingleton != factory.isConfigurationSingletonByDefault) {
        element.setAttribute(SINGLETON, isSingleton.toString())
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

    if (configuration !is UnknownRunConfiguration) {
      manager.writeBeforeRunTasks(configuration)?.let {
        element.addContent(it)
      }
    }

    if (configuration is ModuleBasedConfiguration<*> && configuration.isModuleDirMacroSupported) {
      configuration.configurationModule.module?.let {
        PathMacroManager.getInstance(it).collapsePathsRecursively(element)
      }
    }
    val project = configuration.project
    val macroManager = PathMacroManager.getInstance(project)

    // https://youtrack.jetbrains.com/issue/IDEA-178510
    val projectParentPath = project.basePath?.let { PathUtilRt.getParentPath(it) }
    if (!projectParentPath.isNullOrEmpty()) {
      val replacePathMap = (macroManager as? ProjectPathMacroManager)?.replacePathMap
      if (replacePathMap != null) {
        replacePathMap.addReplacement(projectParentPath, '$' + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$/..", true)
        BasePathMacroManager.collapsePaths(element, true, replacePathMap)
        return
      }
    }
    PathMacroManager.getInstance(project).collapsePathsRecursively(element)
  }

  private fun serializeConfigurationInto(configuration: RunConfiguration, element: Element) {
    if (configuration is PersistentStateComponent<*>) {
      configuration.serializeStateInto(element)
    }
    else {
      configuration.writeExternal(element)
    }
  }

  override fun writeScheme(): Element {
    val element = Element("configuration")
    writeExternal(element)
    return element
  }

  override fun checkSettings(executor: Executor?) {
    val configuration = configuration
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

  override fun getRunnerSettings(runner: ProgramRunner<*>): RunnerSettings? = runnerSettings.getOrCreateSettings(runner)

  override fun getConfigurationSettings(runner: ProgramRunner<*>): ConfigurationPerRunnerSettings? = configurationPerRunnerSettings.getOrCreateSettings(runner)

  override fun getType(): ConfigurationType = factory.type

  public override fun clone(): RunnerAndConfigurationSettingsImpl {
    val copy = RunnerAndConfigurationSettingsImpl(manager, _configuration!!.clone())
    copy.importRunnerAndConfigurationSettings(this)
    return copy
  }

  internal fun importRunnerAndConfigurationSettings(template: RunnerAndConfigurationSettingsImpl) {
    importFromTemplate(template.runnerSettings, runnerSettings)
    importFromTemplate(template.configurationPerRunnerSettings, configurationPerRunnerSettings)

    isSingleton = template.isSingleton
    isEditBeforeRun = template.isEditBeforeRun
    isActivateToolWindowBeforeRun = template.isActivateToolWindowBeforeRun
    level = template.level
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

  override fun compareTo(other: Any): Int = if (other is RunnerAndConfigurationSettings) name.compareTo(other.name) else 0

  override fun toString(): String = "${type.displayName}: ${if (isTemplate) "<template>" else name} (level: $level)"

  private inner class InfoProvider(override val runner: ProgramRunner<*>) : ConfigurationInfoProvider {
    override val configuration: RunConfiguration
      get() = this@RunnerAndConfigurationSettingsImpl.configuration

    override val runnerSettings: RunnerSettings?
      get() = this@RunnerAndConfigurationSettingsImpl.getRunnerSettings(runner)

    override val configurationSettings: ConfigurationPerRunnerSettings?
      get() = this@RunnerAndConfigurationSettingsImpl.getConfigurationSettings(runner)
  }

  override fun getSchemeState(): SchemeState? {
    val configuration = _configuration
    return when (configuration) {
      null -> SchemeState.UNCHANGED
      is UnknownRunConfiguration -> if (configuration.isDoNotStore) SchemeState.NON_PERSISTENT else SchemeState.UNCHANGED
      else -> null
    }
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
      return when {
        runnersById.isEmpty() -> null
        runnersById.size == 1 -> runnersById.firstOrNull()
        else -> {
          LOG.error("More than one runner found for ID: $runnerId")
          for (executor in ExecutorRegistry.getInstance().registeredExecutors) {
            runnersById.firstOrNull { it.canRun(executor.id, configuration)  }?.let {
              return it
            }
          }
          null
        }
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
      unloadedSettings?.mapTo(runnerSettings) { it.clone() }
      runnerSettings.sortWith<Element>(Comparator { o1, o2 ->
        val attributeValue1 = o1.getAttributeValue(RUNNER_ID) ?: return@Comparator 1
        StringUtil.compare(attributeValue1, o2.getAttributeValue(RUNNER_ID), false)
      })
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
        unloadedSettings!!.add(JDOMUtil.internElement(state))
        return
      }

      if (data != null) {
        @Suppress("DEPRECATION")
        (data as JDOMExternalizable).readExternal(state)
      }

      settings.put(runner, data)
      loadedIds.add(runner.runnerId)
    }

    fun getOrCreateSettings(runner: ProgramRunner<*>): T? {
      try {
        return settings.getOrPut(runner) { createSettings(runner) }
      }
      catch (ignored: AbstractMethodError) {
        LOG.error("Update failed for: ${configuration.type.displayName}, runner: ${runner.runnerId}", ExtensionException(runner.javaClass))
        return null
      }
    }
  }
}

// always write method element for shared settings for now due to preserve backward compatibility
private val RunnerAndConfigurationSettings.isNewSerializationAllowed: Boolean
  get() = ApplicationManager.getApplication().isUnitTestMode || !isShared