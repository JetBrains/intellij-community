// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironment.TargetPath
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeContributionUI
import com.intellij.execution.target.getRuntimeType
import com.intellij.execution.target.getTargetType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import com.intellij.util.text.nullize

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration, private val target: TargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  private val targetVolumeContributions = mutableMapOf<VolumeDescriptor, TargetSpecificVolumeContributionUI>()

  override fun createPanel(): DialogPanel {
    return panel {
      row("JDK home path:") {
        textField(config::homePath)
          .comment("The path to the JDK on the target")
      }
      row("JDK version:") {
        textField(config::javaVersionString)
      }

      addVolumeUI(JavaLanguageRuntimeType.APPLICATION_FOLDER_VOLUME)

      hideableRow("Advanced Volume Settings") {
        subRowIndent = 0
        addVolumeUI(JavaLanguageRuntimeType.CLASS_PATH_VOLUME)
        addVolumeUI(JavaLanguageRuntimeType.AGENTS_VOLUME)
      }
    }
  }

  override fun apply() {
    super.apply()
    targetVolumeContributions.forEach { (volume, contribution) ->
      config.setTargetSpecificData(volume, contribution.getValueToApply())
    }
  }

  override fun reset() {
    super.reset()
    targetVolumeContributions.forEach { (volume, contribution) ->
      contribution.resetFrom(config.getTargetSpecificData(volume))
    }
  }

  private fun RowBuilder.addVolumeUI(volumeDescriptor: VolumeDescriptor) {
    row(volumeDescriptor.wizardLabel) {
      textField(getter = { config.getTargetPath(volumeDescriptor).nullize() ?: volumeDescriptor.defaultPath },
                setter = { config.setTargetPath(volumeDescriptor, it.nullize(true)) })
        .comment(volumeDescriptor.description)
    }

    target.getTargetType().createVolumeContributionUI()?.let {
      targetVolumeContributions[volumeDescriptor] = it
      val component = it.createComponent()
      it.resetFrom(config.getTargetSpecificData(volumeDescriptor))
      row("") {
        component()
        largeGapAfter()
      }
    }
  }

  companion object {
    private fun TargetPath.nullize(): String? = when (this) {
      is TargetPath.Persistent -> this.absolutePath.nullize(true)
      is TargetPath.Temporary -> null
    }
  }
}