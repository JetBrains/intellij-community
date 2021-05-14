// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.target.*
import com.intellij.execution.target.LanguageRuntimeType.VolumeDescriptor
import com.intellij.execution.target.TargetEnvironmentType.TargetSpecificVolumeContributionUI
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.HideableDecorator
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.JPanel

class JavaLanguageRuntimeUI(private val config: JavaLanguageRuntimeConfiguration,
                            private val targetType: TargetEnvironmentType<*>,
                            private val targetProvider: Supplier<TargetEnvironmentConfiguration>,
                            private val project: Project) :
  BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

  private lateinit var mainPanel: DialogPanel
  private lateinit var additionalPanel: DialogPanel

  private val targetVolumeContributions = mutableMapOf<VolumeDescriptor, TargetSpecificVolumeContributionUI>()

  override fun createPanel(): DialogPanel {
    val whole = DialogPanel(VerticalLayout(UIUtil.DEFAULT_VGAP))
    mainPanel = panel {
      row(ExecutionBundle.message("java.language.runtime.jdk.home.path")) {
        val cellBuilder: CellBuilder<*>
        if (targetType is BrowsableTargetEnvironmentType) {
          cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, targetType, targetProvider,
                                                               project,
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
    }

    val bottom = JPanel(BorderLayout(0, JBUIScale.scale(UIUtil.LARGE_VGAP)))
    additionalPanel = panel {
      row {
        subRowIndent = 1
        addVolumeUI(JavaLanguageRuntimeType.CLASS_PATH_VOLUME)
        addVolumeUI(JavaLanguageRuntimeType.AGENTS_VOLUME)
      }
    }
    val decorator = HideableDecorator(
      bottom, ExecutionBundle.message("java.language.runtime.separator.advanced.volume.settings"), false)
    decorator.setOn(false)
    decorator.setContentComponent(additionalPanel)

    whole.add(mainPanel, VerticalLayout.TOP)
    whole.add(bottom, VerticalLayout.TOP)

    return whole
  }

  override fun apply() {
    super.apply()
    mainPanel.apply()
    additionalPanel.apply()
    targetVolumeContributions.forEach { volume, contribution ->
      config.setTargetSpecificData(volume, contribution.getConfiguredValue())
    }
  }

  override fun reset() {
    super.reset()
    mainPanel.reset()
    additionalPanel.reset()
    targetVolumeContributions.forEach { volume, contribution ->
      contribution.resetFrom(volume)
    }
  }

  override fun isModified(): Boolean {
    return super.isModified() ||
           mainPanel.isModified() ||
           additionalPanel.isModified()
  }

  private fun RowBuilder.addVolumeUI(volumeDescriptor: VolumeDescriptor) {
    row(volumeDescriptor.wizardLabel) {
      val propertyBinding = PropertyBinding(
        get = { config.getTargetPathValue(volumeDescriptor).nullize(true) ?: volumeDescriptor.defaultPath },
        set = { config.setTargetPath(volumeDescriptor, it.nullize(true)) })

      val cellBuilder: CellBuilder<*>
      if (targetType is BrowsableTargetEnvironmentType) {
        cellBuilder = TargetUIUtil.textFieldWithBrowseButton(this, targetType, targetProvider, project, volumeDescriptor.browsingTitle,
                                                             propertyBinding)
      }
      else {
        cellBuilder = textField(propertyBinding)
      }
      cellBuilder.comment(volumeDescriptor.description)
    }

    targetType.createVolumeContributionUI()?.let {
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