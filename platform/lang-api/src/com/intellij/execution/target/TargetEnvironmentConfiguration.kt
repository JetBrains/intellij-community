// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.openapi.project.Project

abstract class TargetEnvironmentConfiguration(typeId: String) : ContributedConfigurationBase(typeId, RemoteTargetType.EXTENSION_NAME) {

  val runtimes = ContributedConfigurationsList(LanguageRuntimeType.EXTENSION_NAME)

  fun addLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.addConfig(runtime)

  fun removeLanguageRuntime(runtime: LanguageRuntimeConfiguration) = runtimes.removeConfig(runtime)

  fun createRunner(project: Project): TargetEnvironmentFactory = getTargetType().createRunner(project, this)
}

fun <C : TargetEnvironmentConfiguration, T : RemoteTargetType<C>> C.getTargetType(): T = this.getTypeImpl()