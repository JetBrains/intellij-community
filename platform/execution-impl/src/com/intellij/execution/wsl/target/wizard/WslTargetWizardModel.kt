// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target.wizard

import com.intellij.execution.target.*
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class WslTargetWizardModel(override val project: Project,
                           override val subject: WslTargetEnvironmentConfiguration,
                           runtimeType: LanguageRuntimeType<*>?,
                           var distribution: WSLDistribution?) : TargetWizardModel {
  internal var isCustomToolConfiguration: Boolean = false

  override var languageConfigForIntrospection: LanguageRuntimeConfiguration? = runtimeType?.createDefaultConfig()
    private set

  internal fun resetLanguageConfigForIntrospection() {
    languageConfigForIntrospection = languageConfigForIntrospection?.getRuntimeType()?.createDefaultConfig()
  }

  override fun save() {
    subject.distribution = distribution
    subject.displayName = guessName()
  }

  override fun commit() = save()

  @Nls
  fun guessName(): String {
    return distribution.let {
      val name = it?.msId ?: "?"
      "${subject.getTargetType().displayName} - ${name}"
    }
  }
}
