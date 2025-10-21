// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.configurations.*
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.util.regex.Pattern

/**
 * Manages the list of run/debug configurations in a project.
 * @see com.intellij.execution.runners.ProgramRunner
 * @see com.intellij.execution.ExecutionManager
 */
abstract class RunManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunManager {
      if (IS_RUN_MANAGER_INITIALIZED.get(project) != true && !project.isDefault) {
        // https://gist.github.com/develar/5bcf39b3f0ec08f507ec112d73375f2b
        LOG.warn("Must be not called before project components initialized")
      }
      return project.getService(RunManager::class.java)
    }

    @JvmStatic
    fun getInstanceIfCreated(project: Project): RunManager? = project.serviceIfCreated()

    private const val UNNAMED = "Unnamed"

    @JvmField
    @ApiStatus.Internal
    val IS_RUN_MANAGER_INITIALIZED: Key<Boolean> = Key.create("RunManagerInitialized")

    private val LOG = logger<RunManager>()

    @JvmStatic
    fun suggestUniqueName(str: String, currentNames: Collection<String>): String {
      if (!currentNames.contains(str)) {
        return str
      }

      val originalName = extractBaseName(str)
      var i = 1
      while (true) {
        val newName = String.format("%s (%d)", originalName, i)
        if (!currentNames.contains(newName)) {
          return newName
        }
        i++
      }
    }

    private val UNIQUE_NAME_PATTERN = Pattern.compile("(.*?)\\s*\\(\\d+\\)")
    @JvmStatic
    fun extractBaseName(uniqueName: String): String {
      val matcher = UNIQUE_NAME_PATTERN.matcher(uniqueName)
      return if (matcher.matches()) matcher.group(1) else uniqueName
    }

    const val CONFIGURATION_TYPE_FEATURE_ID: String = "com.intellij.configurationType"
  }

  /**
   * Returns the list of all configurations of a specified type.
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  abstract fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration>

  /**
   * Returns the list of [RunnerAndConfigurationSettings] for all configurations of a specified type.
   *
   * Template configuration is not included
   * @param type a run configuration type.
   * @return settings for all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  abstract fun getConfigurationSettingsList(type: ConfigurationType): List<RunnerAndConfigurationSettings>

  fun getConfigurationSettingsList(type: Class<out  ConfigurationType>): List<RunnerAndConfigurationSettings> {
    return getConfigurationSettingsList(ConfigurationTypeUtil.findConfigurationType(type))
  }

  /**
   * Returns the list of all run configurations.
   */
  @Deprecated("", ReplaceWith("allConfigurationsList"))
  @ApiStatus.ScheduledForRemoval
  fun getAllConfigurations(): Array<RunConfiguration> = allConfigurationsList.toTypedArray()

  /**
   * Returns the list of all run configurations.
   */
  abstract val allConfigurationsList: List<RunConfiguration>

  /**
   * Returns the list of all run configurations settings.
   */
  abstract val allSettings: List<RunnerAndConfigurationSettings>

  /**
   * Returns the list of all temporary run configurations settings.
   * @see RunnerAndConfigurationSettings.isTemporary
   */
  abstract val tempConfigurationsList: List<RunnerAndConfigurationSettings>

  /**
   * Saves the specified temporary run settings and makes it a permanent one.
   * @param settings the temporary settings to save.
   */
  abstract fun makeStable(settings: RunnerAndConfigurationSettings)

  /**
   * The selected item in the run/debug configurations combobox.
   */
  abstract var selectedConfiguration: RunnerAndConfigurationSettings?

  /**
   * should set selected configuration from context
   */
  abstract fun shouldSetRunConfigurationFromContext(): Boolean

  abstract fun isRiderRunWidgetActive(): Boolean

  /**
   * Creates a configuration of the specified type with the specified name. Note that you need to call
   * [.addConfiguration] if you want the configuration to be persisted in the project.
   * @param name the name of the configuration to create (should be unique and not equal to any other existing configuration)
   * @param factory the factory instance.
   * @see RunManager.suggestUniqueName
   */
  abstract fun createConfiguration(@NlsSafe name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings

  fun createConfiguration(name: String, typeClass: Class<out ConfigurationType>): RunnerAndConfigurationSettings {
    return createConfiguration(name, ConfigurationTypeUtil.findConfigurationType(typeClass).configurationFactories.first())
  }

  @Deprecated("", ReplaceWith("createConfiguration(name, factory)"))
  fun createRunConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings = createConfiguration(name, factory)

  /**
   * Creates a configuration settings object based on a specified [RunConfiguration]. Note that you need to call
   * [addConfiguration] if you want the configuration to be persisted in the project.
   * @param runConfiguration the run configuration
   * @param factory the factory instance.
   */
  abstract fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory): RunnerAndConfigurationSettings

  /**
   * Returns the template settings for the specified configuration type.
   * @param factory the configuration factory.
   */
  abstract fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettings

  /**
   * Adds the specified run configuration to the list of run configurations.
   */
  abstract fun addConfiguration(settings: RunnerAndConfigurationSettings)

  /**
   * This method is deprecated because there are different ways of storing run configuration in a file.
   * Clients should use [addConfiguration(RunnerAndConfigurationSettings)] and before that, if needed,
   * [RunnerAndConfigurationSettings#storeInDotIdeaFolder()], [RunnerAndConfigurationSettings#storeInArbitraryFileInProject(String)]
   * or [RunnerAndConfigurationSettings#storeInLocalWorkspace()].
   * @see RunnerAndConfigurationSettings.storeInDotIdeaFolder
   * @see RunnerAndConfigurationSettings.storeInArbitraryFileInProject
   * @see RunnerAndConfigurationSettings.storeInLocalWorkspace
   */
  @Deprecated("There are different ways of storing run configuration in a file. " +
              "Clients should use RunManager.addConfiguration(RunnerAndConfigurationSettings) and before that, if needed, " +
              "RunnerAndConfigurationSettings.storeInDotIdeaFolder(), storeInArbitraryFileInProject(String) or storeInLocalWorkspace().")
  fun addConfiguration(settings: RunnerAndConfigurationSettings, storeInDotIdeaFolder: Boolean) {
    if (storeInDotIdeaFolder) {
      settings.storeInDotIdeaFolder()
    }
    else {
      settings.storeInLocalWorkspace()
    }
    addConfiguration(settings)
  }

  /**
   * Marks the specified run configuration as recently used (the temporary run configurations are deleted in LRU order).
   * @param profile the run configuration to mark as recently used.
   */
  abstract fun refreshUsagesList(profile: RunProfile)

  abstract fun hasSettings(settings: RunnerAndConfigurationSettings): Boolean

  fun suggestUniqueName(name: String?, type: ConfigurationType?): String {
    val settingsList = if (type == null) allSettings else getConfigurationSettingsList(type)
    return suggestUniqueName(name.nullize() ?: UNNAMED, settingsList.map { it.name })
  }

  /**
   * Sets unique name if existing one is not 'unique'.
   * If the settings type is not null (e.g., settings may be provided by plugin that is unavailable after IDE restart,
   * so the type would be suddenly null), the name will be chosen unique for a certain type.
   * Otherwise, the name will be unique among all configurations.
   * @return `true` if name was changed
   */
  fun setUniqueNameIfNeeded(settings: RunnerAndConfigurationSettings): Boolean {
    val oldName = settings.name
    settings.name = suggestUniqueName(oldName.takeIf { it.isNotBlank() } ?: UNNAMED, settings.type)
    return oldName != settings.name
  }

  /**
   * Sets unique name if existing one is not 'unique' for corresponding configuration type
   * @return `true` if name was changed
   */
  fun setUniqueNameIfNeeded(configuration: RunConfiguration): Boolean {
    val oldName = configuration.name
    @Suppress("UsePropertyAccessSyntax")
    configuration.setName(suggestUniqueName(oldName.takeIf { it.isNotBlank() } ?: UNNAMED, configuration.type))
    return oldName != configuration.name
  }

  abstract fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings?

  abstract fun findSettings(configuration: RunConfiguration): RunnerAndConfigurationSettings?

  fun findConfigurationByTypeAndName(typeId: String, name: String): RunnerAndConfigurationSettings? = allSettings.firstOrNull { typeId == it.type.id && name == it.name }

  fun findConfigurationByTypeAndName(type: ConfigurationType, name: String): RunnerAndConfigurationSettings? = allSettings.firstOrNull { type === it.type && name == it.name }

  abstract fun removeConfiguration(settings: RunnerAndConfigurationSettings?)

  abstract fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?)

  /**
   * Returns true if the provided configuration settings object represents a template used to create other configurations
   * of the same type.
   *
   * Plugins should use `RunManager#findSettings()?.isTemplate() ?: false`.
   *
   * Due to historical reasons, [findSettings] searches by name in addition to instance and this behavior is bad for [isTemplate],
   * so, for now, the client uses this instead.
   *
   * @return `true` if the configuration is a template, `false` otherwise.
   */
  @ApiStatus.Internal
  abstract fun isTemplate(configuration: RunConfiguration): Boolean
}