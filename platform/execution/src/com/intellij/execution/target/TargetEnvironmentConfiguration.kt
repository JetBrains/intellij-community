// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.execution.configurations.RuntimeConfigurationWarning
import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import java.util.*

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetType"][TargetEnvironmentType.EXTENSION_NAME] extension point.
 *
 * To be useful, target configuration should normally include at least one language runtime configuration, either introspected
 * or explicitly edited by the user.
 *
 * All available target configurations can be retrieved via [com.intellij.execution.target.TargetEnvironmentsManager]
 */
abstract class TargetEnvironmentConfiguration(typeId: String) : ContributedConfigurationBase(typeId, TargetEnvironmentType.EXTENSION_NAME) {
  /**
   * Allows implementing links to the configuration. F.e. the link for the project default target.
   *
   * Note. Some initializations with generated UUID are excessive because they will be overridden during the deserialization.
   *
   * This field is only persisted by [TargetEnvironmentsManager]. It will be regenerated on each launch unless you use
   * this manager.
   * See also [TargetConfigurationWithId.targetAndTypeId]
   */
  var uuid: String = UUID.randomUUID().toString()
    internal set

  val runtimes: ContributedConfigurationsList<LanguageRuntimeConfiguration, LanguageRuntimeType<*>> = ContributedConfigurationsList(LanguageRuntimeType.EXTENSION_NAME)

  fun addLanguageRuntime(runtime: LanguageRuntimeConfiguration): Unit = runtimes.addConfig(runtime)

  fun removeLanguageRuntime(runtime: LanguageRuntimeConfiguration): Boolean = runtimes.removeConfig(runtime)

  fun createEnvironmentRequest(project: Project?): TargetEnvironmentRequest = getTargetType().createEnvironmentRequest(project, this)

  abstract var projectRootOnTarget: String

  /**
   * Validates this configuration. By default delegates validation to each of the attached language runtimes.
   * Subclasses may override.
   */
  @Throws(RuntimeConfigurationException::class)
  open fun validateConfiguration() {
    with(runtimes.resolvedConfigs()) {
      if (isEmpty()) {
        throw RuntimeConfigurationWarning(
          ExecutionBundle.message("TargetEnvironmentConfiguration.error.language.runtime.not.configured"))
      }
      forEach {
        it.validateConfiguration()
      }
    }
  }

  abstract class TargetBaseState : BaseState() {
    var uuid: String? by string()
  }
}

fun <C : TargetEnvironmentConfiguration, T : TargetEnvironmentType<C>> C.getTargetType(): T = this.getTypeImpl()