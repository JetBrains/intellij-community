// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.wsl.target.wizard.WslTargetIntrospectionStep
import com.intellij.execution.wsl.target.wizard.WslTargetLanguageStep
import com.intellij.execution.wsl.target.wizard.WslTargetWizardModel
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import javax.swing.Icon

class WslTargetType : TargetEnvironmentType<WslTargetEnvironmentConfiguration>(TYPE_ID) {

  override fun isSystemCompatible(): Boolean = SystemInfo.isWin10OrNewer

  override val displayName: String
    get() = DISPLAY_NAME

  override val icon: Icon = AllIcons.RunConfigurations.Wsl

  override fun createSerializer(config: WslTargetEnvironmentConfiguration) = config

  override fun createDefaultConfig(): WslTargetEnvironmentConfiguration {
    return WslTargetEnvironmentConfiguration()
  }

  override fun providesNewWizard(project: Project, runtimeType: LanguageRuntimeType<*>?) = true

  override fun createStepsForNewWizard(project: Project,
                                       configToConfigure: WslTargetEnvironmentConfiguration,
                                       runtimeType: LanguageRuntimeType<*>?): List<AbstractWizardStepEx> {
    val model = WslTargetWizardModel(project, configToConfigure, runtimeType, null)
    return listOf(WslTargetIntrospectionStep(model), WslTargetLanguageStep(model))
  }

  override fun createEnvironmentFactory(project: Project, config: WslTargetEnvironmentConfiguration): TargetEnvironmentFactory {
    return WslTargetEnvironmentFactory(config)
  }

  override fun createConfigurable(project: Project,
                                  config: WslTargetEnvironmentConfiguration,
                                  defaultLanguage: LanguageRuntimeType<*>?,
                                  parentConfigurable: Configurable?) = WslTargetConfigurable(config, project)

  override fun duplicateConfig(config: WslTargetEnvironmentConfiguration): WslTargetEnvironmentConfiguration =
    duplicateTargetConfiguration(this, config)

  companion object {
    const val TYPE_ID = "wsl"
    @NlsSafe
    const val DISPLAY_NAME = "WSL"
  }
}
