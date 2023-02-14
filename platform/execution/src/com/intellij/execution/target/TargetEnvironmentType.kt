// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.TargetEnvironmentType.Companion.EXTENSION_NAME
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Contributed type for ["com.intellij.executionTargetType"][EXTENSION_NAME] extension point
 *
 * Contributed instances of this class define a type of target environments that can be created by user
 * and configured to run processes on.
 */
//todo[remoteServers]: suggest "predefined" configurations (e.g one per every configured SFTP connection)
abstract class TargetEnvironmentType<C : TargetEnvironmentConfiguration>(id: String) : ContributedTypeBase<C>(id) {

  /**
   * Returns true if configuration of given type should be run locally without additional preparations in advance
   */
  open fun isLocalTarget(): Boolean = false

  /**
   * Returns true if configurations of given type can be run on this OS.
   */
  open fun isSystemCompatible(): Boolean = true

  /**
   * Returns true if the new configuration of given type may be set up by the user iteratively with the help of [createStepsForNewWizard]
   */
  open fun providesNewWizard(project: Project, runtimeType: LanguageRuntimeType<*>?): Boolean = false

  /**
   * Prepares the wizard for setting up the new configuration instance of this type.
   */
  open fun createStepsForNewWizard(project: Project, configToConfigure: C, runtimeType: LanguageRuntimeType<*>?)
    : List<AbstractWizardStepEx>? = null

  /**
   * Instantiates a new environment factory for given prepared [configuration][config].
   */
  abstract fun createEnvironmentRequest(project: Project?, config: C): TargetEnvironmentRequest

  abstract fun createConfigurable(project: Project,
                                  config: C,
                                  defaultLanguage: LanguageRuntimeType<*>?,
                                  parentConfigurable: Configurable?): Configurable

  /**
   * The optional target-specific contribution to all the volumes configurables defined by the respected
   */
  open fun createVolumeContributionUI(): TargetSpecificVolumeContributionUI? = null

  companion object {
    /**
     * For retrieving the list of target types available for Run Configurations use [getTargetTypesForRunConfigurations].
     */
    @JvmField
    val EXTENSION_NAME = ExtensionPointName.create<TargetEnvironmentType<*>>("com.intellij.executionTargetType")

    /**
     * Returns the types that might be shown in "Run on" for Run Configurations.
     *
     * The classes annotated with [HideFromRunOn] are excluded.
     *
     * @see HideFromRunOn
     */
    @JvmStatic
    @ApiStatus.Experimental
    fun getTargetTypesForRunConfigurations(): List<TargetEnvironmentType<*>> =
      EXTENSION_NAME.extensionList.filter { !it.javaClass.isAnnotationPresent(HideFromRunOn::class.java) }

    @JvmStatic
    fun <Type, Config, State> duplicateTargetConfiguration(type: Type, template: Config): Config
      where Config : PersistentStateComponent<State>,
            Config : TargetEnvironmentConfiguration,
            Type : TargetEnvironmentType<Config> {

      return duplicatePersistentComponent(type, template).also { copy ->
        template.runtimes.resolvedConfigs().map { next ->
          copy.runtimes.addConfig(next.getRuntimeType().duplicateConfig(next))
        }
      }
    }

    @Throws(IllegalStateException::class)
    @JvmStatic
    fun <T : TargetEnvironmentType<*>> findInstance(targetClass: Class<T>): T {
      return EXTENSION_NAME.extensionList.filterIsInstance(targetClass).firstOrNull()
             ?: throw IllegalStateException("Cannot find TargetEnvironmentType instance of $targetClass")
    }
  }

  /**
   * Custom UI component for editing of the target specific volume data. The language runtime is expected to create separate editor instance
   * for each of its [LanguageRuntimeType.volumeDescriptors] descriptors. The data configured by the user will be stored in the
   * [LanguageRuntimeConfiguration.getTargetSpecificData], and will be made available for the target via the [TargetEnvironmentRequest].
   *
   * Currently, only [TargetEnvironment.UploadRoot.volumeData] is actually supported.
   *
   * The [TargetSpecificVolumeData] configured by the user in this editor,
   * will be passed at the time of the TargetEnviron
   */
  interface TargetSpecificVolumeContributionUI {
    fun createComponent(): JComponent

    /**
     * [storedData] previously serialized data originally produced by [TargetSpecificVolumeData#toStorableMap] call
     */
    fun resetFrom(storedData: Map<String, String>)

    fun getConfiguredValue(): TargetSpecificVolumeData
  }

  /**
   * Marker interface for all the target specific data that has to be configured by the user for each volume, defined in the language runtime.
   * E.g, the docker target may request user to define whether particular path has to be mounted as volume, or copied to target via `docker cp`.
   */
  interface TargetSpecificVolumeData {
    /**
     * Defines serialization format for target specific volume data. The result map is a separate copy of the real data,
     * its modifications do not affect the actual data.
     */
    fun toStorableMap(): Map<String, String>
  }
}

inline fun <reified T : TargetEnvironmentType<*>> findTargetEnvironmentType() = TargetEnvironmentType.findInstance(T::class.java)
