// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeContributionUI
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.util.text.nullize
import java.util.function.Supplier

abstract class LanguageRuntimeConfigurable(private val config: LanguageRuntimeConfiguration,
                                           private val targetType: TargetEnvironmentType<*>,
                                           private val targetProvider: Supplier<out TargetEnvironmentConfiguration>,
                                           private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  private val targetVolumeContributions = mutableMapOf<VolumeDescriptor, TargetSpecificVolumeContributionUI>()

  override fun apply() {
    super.apply()
    targetVolumeContributions.forEach { (volume, contribution) ->
      config.setTargetSpecificData(volume, contribution.getConfiguredValue())
    }
  }

  override fun reset() {
    super.reset()
    targetVolumeContributions.forEach { (volume, contribution) ->
      contribution.resetFrom(volume)
    }
  }

  protected fun Panel.addVolumeUI(volumeDescriptor: VolumeDescriptor) {
    row(volumeDescriptor.wizardLabel) {
      val propertyBinding = MutableProperty(
        getter = { config.getTargetPathValue(volumeDescriptor).nullize(true) ?: volumeDescriptor.defaultPath },
        setter = { config.setTargetPath(volumeDescriptor, it.nullize(true)) })

      browsableTextField(volumeDescriptor.browsingTitle, propertyBinding)
        .comment(volumeDescriptor.description)
    }

    targetType.createVolumeContributionUI()?.let {
      targetVolumeContributions[volumeDescriptor] = it
      val component = it.createComponent()
      it.resetFrom(volumeDescriptor)
      row("") {
        cell(component)
          .align(AlignX.FILL)
      }
    }
  }

  protected fun Row.browsableTextField(@NlsContexts.DialogTitle title: String, property: MutableProperty<String>): Cell<*> =
    if (targetType is BrowsableTargetEnvironmentType) {
      textFieldWithBrowseTargetButton(targetType, targetProvider, project, title, property)
        .align(AlignX.FILL)
    }
    else {
      textField()
        .bindText(property)
        .align(AlignX.FILL)
    }

  private fun TargetSpecificVolumeContributionUI.resetFrom(volume: VolumeDescriptor) {
    this.resetFrom(config.getTargetSpecificData(volume)?.toStorableMap() ?: emptyMap())
  }
}