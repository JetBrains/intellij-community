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
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize
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
      return ServiceManager.getService(project, RunManager::class.java)
    }

    @JvmStatic
    fun suggestUniqueName(str: String, currentNames: Collection<String>): String {
      if (!currentNames.contains(str)) return str

      val matcher = Pattern.compile("(.*?)\\s*\\(\\d+\\)").matcher(str)
      val originalName = if (matcher.matches()) matcher.group(1) else str
      var i = 1
      while (true) {
        val newName = String.format("%s (%d)", originalName, i)
        if (!currentNames.contains(newName)) return newName
        i++
      }
    }
  }

  /**
   * Returns the list of all registered configuration types.
   */
  abstract val configurationFactories: Array<ConfigurationType>

  abstract val configurationFactoriesWithoutUnknown: List<ConfigurationType>

  /**
   * Returns the list of all configurations of a specified type.
   * @param type a run configuration type.
   * @return all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  @Deprecated("", ReplaceWith("getConfigurationsList(type)"))
  fun getConfigurations(type: ConfigurationType) = getConfigurationsList(type).toTypedArray()

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
  fun getConfigurationSettings(type: ConfigurationType) = getConfigurationSettingsList(type).toTypedArray()

  /**
   * Returns the list of [RunnerAndConfigurationSettings] for all configurations of a specified type.
   *
   * Template configuration is not included
   * @param type a run configuration type.
   * @return settings for all configurations of the type, or an empty array if no configurations of the type are defined.
   */
  abstract fun getConfigurationSettingsList(type: ConfigurationType): List<RunnerAndConfigurationSettings>

  /**
   * Returns the list of all run configurations.
   */
  @Deprecated("", ReplaceWith("allConfigurationsList"))
  fun getAllConfigurations() = allConfigurationsList.toTypedArray()

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

  fun createRunConfiguration(name: String, factory: ConfigurationFactory) = createConfiguration(name, factory)

  /**
   * Creates a configuration settings object based on a specified [RunConfiguration]. Note that you need to call
   * [.addConfiguration] if you want the configuration to be persisted in the project.
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
   * Adds the specified run configuration to the list of run configurations stored in the project.
   * @param settings the run configuration settings.
   * @param isShared true if the configuration is marked as shared (stored in the versioned part of the project files), false if it's local
   * *                 (stored in the workspace file).
   */
  abstract fun addConfiguration(settings: RunnerAndConfigurationSettings, isShared: Boolean)

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
  fun setUniqueNameIfNeed(settings: RunnerAndConfigurationSettings): Boolean {
    val oldName = settings.name
    settings.name = suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), settings.type)
    return oldName != settings.name
  }

  /**
   * Sets unique name if existing one is not 'unique' for corresponding configuration type
   * @return `true` if name was changed
   */
  fun setUniqueNameIfNeed(configuration: RunConfiguration): Boolean {
    val oldName = configuration.name
    configuration.name = suggestUniqueName(StringUtil.notNullize(oldName, UNNAMED), configuration.type)
    return oldName != configuration.name
  }

  abstract fun getConfigurationType(typeName: String): ConfigurationType?

  abstract fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings?

  fun findConfigurationByTypeAndName(typeId: String, name: String) = allSettings.firstOrNull { typeId == it.type.id && name == it.name }

  abstract fun removeConfiguration(settings: RunnerAndConfigurationSettings?)

  abstract fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?)
}

private val UNNAMED = "Unnamed"

val IS_RUN_MANAGER_INITIALIZED = Key.create<Boolean>("RunManagerInitialized")
private  val LOG = Logger.getInstance(RunManager::class.java)