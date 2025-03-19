// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.*
import com.intellij.execution.wsl.target.wizard.WslTargetCustomToolStep
import com.intellij.execution.wsl.target.wizard.WslTargetIntrospectionStep
import com.intellij.execution.wsl.target.wizard.WslTargetLanguageStep
import com.intellij.execution.wsl.target.wizard.WslTargetWizardModel
import com.intellij.execution.wsl.ui.browseWslPath
import com.intellij.icons.AllIcons
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.TextAccessor
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.ActionListener
import java.util.function.Supplier
import javax.swing.Icon

class WslTargetType : TargetEnvironmentType<WslTargetEnvironmentConfiguration>(TYPE_ID), BrowsableTargetEnvironmentType {

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
    val isCustomToolConfiguration = runtimeType is CustomToolLanguageRuntimeType
    model.isCustomToolConfiguration = isCustomToolConfiguration
    return listOf(
      WslTargetIntrospectionStep(model),
      if (isCustomToolConfiguration) WslTargetCustomToolStep(model) else WslTargetLanguageStep(model)
    )
  }

  override fun createEnvironmentRequest(project: Project?, config: WslTargetEnvironmentConfiguration): TargetEnvironmentRequest {
    return WslTargetEnvironmentRequest(config)
  }

  @ApiStatus.Internal
  override fun createConfigurable(project: Project,
                                  config: WslTargetEnvironmentConfiguration,
                                  defaultLanguage: LanguageRuntimeType<*>?,
                                  parentConfigurable: Configurable?) = WslTargetConfigurable(config, project)

  override fun duplicateConfig(config: WslTargetEnvironmentConfiguration): WslTargetEnvironmentConfiguration =
    duplicateTargetConfiguration(this, config)

  override fun <T : Component> createBrowser(project: Project,
                                             title: String?,
                                             textComponentAccessor: TextComponentAccessor<T>,
                                             component: T,
                                             configurationSupplier: Supplier<out TargetEnvironmentConfiguration>,
                                             targetBrowserHints: TargetBrowserHints): ActionListener = ActionListener {
    val configuration = configurationSupplier.get() as WslTargetEnvironmentConfiguration
    configuration.distribution?.let {
      val textAccessor = object : TextAccessor {
        override fun setText(text: String) = textComponentAccessor.setText(component, text)
        override fun getText() = textComponentAccessor.getText(component)
      }
      browseWslPath(textAccessor,
                    it,
                    component,
                    accessWindowsFs = targetBrowserHints.showLocalFsInBrowser,
                    customFileDescriptor = targetBrowserHints.customFileChooserDescriptor)
      return@ActionListener
    }
  }

  companion object {
    const val TYPE_ID = "wsl"

    @NlsSafe
    const val DISPLAY_NAME = "WSL"
  }
}
