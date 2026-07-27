// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.target.LanguageRuntimeConfiguration
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetWizardModel
import com.intellij.execution.target.getRuntimeType
import com.intellij.execution.target.getTargetType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class WslTargetWizardModel private constructor(
  override val project: Project,
  override val subject: WslTargetEnvironmentConfiguration,
  runtimeType: LanguageRuntimeType<*>?,
  var distribution: WSLDistribution?,
  override val module: Module?,
) : TargetWizardModel() {

  constructor(
    project: Project,
    subject: WslTargetEnvironmentConfiguration,
    runtimeType: LanguageRuntimeType<*>?,
    distribution: WSLDistribution?,
  ) : this(project, subject, runtimeType, distribution, null)

  constructor(
    module: Module,
    subject: WslTargetEnvironmentConfiguration,
    runtimeType: LanguageRuntimeType<*>?,
    distribution: WSLDistribution?,
  ) : this(module.project, subject, runtimeType, distribution, module)

  internal var isCustomToolConfiguration: Boolean = false

  override var languageConfigForIntrospection: LanguageRuntimeConfiguration? = runtimeType?.createDefaultConfig()
    private set

  internal fun resetLanguageConfigForIntrospection() {
    languageConfigForIntrospection = languageConfigForIntrospection?.getRuntimeType()?.createDefaultConfig()
  }

  override fun applyChanges() {
    subject.distribution = distribution
    subject.displayName = guessName()
  }

  @Nls
  fun guessName(): String {
    return distribution.let {
      val name = it?.msId ?: "?"
      "${subject.getTargetType().displayName} - ${name}"
    }
  }
}
