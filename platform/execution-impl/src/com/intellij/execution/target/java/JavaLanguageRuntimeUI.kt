// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.target.*
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeContributionUI
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration,
                            private val target: TargetEnvironmentConfiguration,
                            private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  private val targetVolumeContributions = mutableMapOf<VolumeDescriptor, TargetSpecificVolumeContributionUI>()

  override fun createPanel(): DialogPanel {
    return panel {
      row(ExecutionBundle.message("java.language.runtime.jdk.home.path")) {
        val cellBuilder: CellBuilder<*>
        if (target is BrowsableTargetEnvironmentConfiguration) {
          cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, target, project,
                                                               ExecutionBundle.message("java.language.runtime.jdk.home.path.title"),
                                                               config::homePath.toBinding())
        }
        else {
          cellBuilder = textField(config::homePath)
        }
        cellBuilder.comment(ExecutionBundle.message("java.language.runtime.text.path.to.jdk.on.target"))
      }
      row(ExecutionBundle.message("java.language.runtime.jdk.version")) {
        textField(config::javaVersionString)
      }

      addVolumeUI(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME)

      hideableRow(ExecutionBundle.message("java.language.runtime.separator.advanced.volume.settings")) {
        subRowIndent = 0
        addVolumeUI(JavaLanguageRuntimeType.CLASS_PATH_VOLUME)
        addVolumeUI(JavaLanguageRuntimeType.AGENTS_VOLUME)
      }
    }
  }

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

  private fun RowBuilder.addVolumeUI(volumeDescriptor: VolumeDescriptor) {
    row(volumeDescriptor.wizardLabel) {
      val propertyBinding = PropertyBinding(
        get = { config.getTargetPathValue(volumeDescriptor).nullize(true) ?: volumeDescriptor.defaultPath },
        set = { config.setTargetPath(volumeDescriptor, it.nullize(true)) })

      val cellBuilder: CellBuilder<*>
      if (target is BrowsableTargetEnvironmentConfiguration) {
        cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, target, project, volumeDescriptor.browsingTitle, propertyBinding)
      }
      else {
        cellBuilder = textField(propertyBinding)
      }
      cellBuilder.comment(volumeDescriptor.description)
    }

    target.getTargetType().createVolumeContributionUI()?.let {
      targetVolumeContributions[volumeDescriptor] = it
      val component = it.createComponent()
      it.resetFrom(volumeDescriptor)
      row("") {
        component()
        largeGapAfter()
      }
    }
  }

  private fun TargetSpecificVolumeContributionUI.resetFrom(volume: VolumeDescriptor) {
    this.resetFrom(config.getTargetSpecificData(volume)?.toStorableMap() ?: emptyMap())
  }
}