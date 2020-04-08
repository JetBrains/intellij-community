// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.openapi.project.Project

/**
 * Base class for configuration instances contributed by the ["com.intellij.executionTargetType"][TargetEnvironmentType.EXTENSION_NAME] extension point.
 *
 * To be useful, target configuration should normally include at least one language runtime configuration, either introspected
 * or explicitly edited by the user.
 *
 * All available target configurations can be retrieved via [com.intellij.execution.target.TargetEnvironmentsManager]
 */
abstract class TargetEnvironmentConfiguration(typeId: String) : ContributedConfigurationBase(typeId, TargetEnvironmentType.EXTENSION_NAME) {

  val runtimes = ContributedConfigurationsList(LanguageRuntimeType.EXTENSION_NAME)

  fun addLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.addConfig(runtime)

  fun removeLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.removeConfig(runtime)

  fun createEnvironmentFactory(project: Project): TargetEnvironmentFactory = getTargetType().createEnvironmentFactory(project, this)
}

fun <C : TargetEnvironmentConfiguration, T : TargetEnvironmentType<C>> C.getTargetType(): T = this.getTypeImpl()