// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import javax.swing.Icon

class WslTargetType : TargetEnvironmentType<WslTargetEnvironmentConfiguration>(TYPE_ID) {

  override fun isSystemCompatible(): Boolean = SystemInfo.isWin10OrNewer

  override val displayName: String
    @NlsSafe get() = "WSL"

  override val icon: Icon = AllIcons.RunConfigurations.Wsl

  override fun createSerializer(config: WslTargetEnvironmentConfiguration) = config

  override fun createDefaultConfig(): WslTargetEnvironmentConfiguration {
    return WslTargetEnvironmentConfiguration()
  }

  override fun createEnvironmentFactory(project: Project, config: WslTargetEnvironmentConfiguration): TargetEnvironmentFactory {
    return WslTargetEnvironmentFactory(config)
  }

  override fun createConfigurable(project: Project,
                                  config: WslTargetEnvironmentConfiguration,
                                  defaultLanguage: LanguageRuntimeType<*>?,
                                  parentConfigurable: Configurable?) = WslTargetConfigurable(config)

  override fun duplicateConfig(config: WslTargetEnvironmentConfiguration): WslTargetEnvironmentConfiguration =
    duplicateTargetConfiguration(this, config)

  companion object {
    const val TYPE_ID = "wsl"
  }
}
