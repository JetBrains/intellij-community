// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.util.regex.Pattern

/**
 * Manages the list of run/debug configurations in a project.
 * @see RunnerRegistry
 * @see ExecutionManager
 */
abstract class RunManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunManager {
      if (IS_RUN_MANAGER_INITIALIZED.get(project) != true) {
        // https://gist.github.com/develar/5bcf39b3f0ec08f507ec112d73375f2b
        LOG.debug("Must be not called before project components initialized")
      }
      return project.getService(RunManager::class.java)
    }

    @JvmStatic
    fun suggestUniqueName(str: String, currentNames: Collection<String>): String {
      if (!currentNames.contains(str)) return str

      val originalName = extractBaseName(str)
      var i = 1
      while (true) {
        val newName = String.format("%s (%d)", originalName, i)
        if (!currentNames.contains(newName)) return newName
        i++
      }
    }

    private val UNIQUE_NAME_PATTERN = Pattern.compile("(.*?)\\s*\\(\\d+\\)")
    @JvmStatic
    fun extractBaseName(uniqueName: String): String {
      val matcher = UNIQUE_NAME_PATTERN.matcher(uniqueName)
      return if (matcher.matches()) matcher.group(1) else uniqueName
    }
  }

  /**
   * Returns the list of all configurations of a specified type.
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @Deprecated("", ReplaceWith("getConfigurationsList(type)"))
  fun getConfigurations(type: ConfigurationType): Array<RunConfiguration> = getConfigurationsList(type).toTypedArray()

  /**
   * Returns the list of all configurations of a specified type.
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  abstract fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration>

  /**
   * Returns the list of [RunnerAndConfigurationSettings] for all configurations of a specified type.
   * @param type a run configuration type.
   * @return settings for all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @Deprecated("", ReplaceWith("getConfigurationSettingsList(type)"))
  fun getConfigurationSettings(type: ConfigurationType): Array<RunnerAndConfigurationSettings> = getConfigurationSettingsList(type).toTypedArray()

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
   * Creates a configuration of the specified type with the specified name. Note that you need to call
   * [.addConfiguration] if you want the configuration to be persisted in the project.
   * @param name the name of the configuration to create (should be unique and not equal to any other existing configuration)
   * @param factory the factory instance.
   * @see RunManager.suggestUniqueName
   */
  abstract fun createConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings

  fun createConfiguration(name: String, typeClass: Class<out ConfigurationType>): RunnerAndConfigurationSettings {
    return createConfiguration(name, ConfigurationTypeUtil.findConfigurationType(typeClass).configurationFactories.first())
  }

  @Deprecated("", ReplaceWith("createConfiguration(name, factory)"))
  fun createRunConfiguration(name: String, factory: ConfigurationFactory) = createConfiguration(name, factory)

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
   * Sets unique name if existing one is not 'unique'
   * If settings type is not null (for example settings may be provided by plugin that is unavailable after IDE restart, so type would be suddenly null)
   * name will be chosen unique for certain type otherwise name will be unique among all configurations
   * @return `true` if name was changed
   */
  fun setUniqueNameIfNeeded(settings: RunnerAndConfigurationSettings): Boolean {
    val oldName = settings.name
    settings.name = suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), settings.type)
    return oldName != settings.name
  }

  @Deprecated("The method name is grammatically incorrect", replaceWith = ReplaceWith("this.setUniqueNameIfNeeded(settings)"))
  fun setUniqueNameIfNeed(settings: RunnerAndConfigurationSettings): Boolean = setUniqueNameIfNeeded(settings)

  /**
   * Sets unique name if existing one is not 'unique' for corresponding configuration type
   * @return `true` if name was changed
   */
  fun setUniqueNameIfNeeded(configuration: RunConfiguration): Boolean {
    val oldName = configuration.name
    @Suppress("UsePropertyAccessSyntax")
    configuration.setName(suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), configuration.type))
    return oldName != configuration.name
  }

  @Deprecated("The method name is grammatically incorrect", replaceWith = ReplaceWith("this.setUniqueNameIfNeeded(configuration)"))
  fun setUniqueNameIfNeed(configuration: RunConfiguration): Boolean = setUniqueNameIfNeeded(configuration)

  @Deprecated("Use ConfigurationTypeUtil", ReplaceWith("ConfigurationTypeUtil.findConfigurationType(typeName)", "com.intellij.execution.configurations.ConfigurationTypeUtil"))
  fun getConfigurationType(typeName: String) = ConfigurationTypeUtil.findConfigurationType(typeName)

  abstract fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings?

  abstract fun findSettings(configuration: RunConfiguration): RunnerAndConfigurationSettings?

  fun findConfigurationByTypeAndName(typeId: String, name: String) = allSettings.firstOrNull { typeId == it.type.id && name == it.name }

  fun findConfigurationByTypeAndName(type: ConfigurationType, name: String) = allSettings.firstOrNull { type === it.type && name == it.name }

  abstract fun removeConfiguration(settings: RunnerAndConfigurationSettings?)

  abstract fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?)

  // due to historical reasons findSettings() searches by name in addition to instance and this behavior is bad for isTemplate,
  // so, client cannot for now use `findSettings()?.isTemplate() ?: false`.
  @ApiStatus.Internal
  abstract fun isTemplate(configuration: RunConfiguration): Boolean
}

private const val UNNAMED = "Unnamed"

val IS_RUN_MANAGER_INITIALIZED = Key.create<Boolean>("RunManagerInitialized")
private val LOG = Logger.getInstance(RunManager::class.java)