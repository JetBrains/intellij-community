// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.LanguageRuntimeType.Companion.EXTENSION_NAME
import com.intellij.execution.target.TargetEnvironmentType.Companion.EXTENSION_NAME
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project

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

  companion object {
    @JvmField
    val EXTENSION_NAME = ExtensionPointName.create<TargetEnvironmentType<*>>("com.intellij.executionTargetType")
  }
}