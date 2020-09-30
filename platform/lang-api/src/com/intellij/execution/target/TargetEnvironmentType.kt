// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.TargetEnvironmentType.Companion.EXTENSION_NAME
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
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
  abstract fun createEnvironmentFactory(project: Project, config: C): TargetEnvironmentFactory

  abstract fun createConfigurable(project: Project, config: C): Configurable

  /**
   * The optional target-specific contribution to all the volumes configurables defined by the respected
   */
  open fun createVolumeContributionUI(): TargetSpecificVolumeContributionUI? = null

  companion object {
    @JvmField
    val EXTENSION_NAME = ExtensionPointName.create<TargetEnvironmentType<*>>("com.intellij.executionTargetType")
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