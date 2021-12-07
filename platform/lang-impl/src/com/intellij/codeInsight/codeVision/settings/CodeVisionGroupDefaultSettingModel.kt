// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionBundle
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

open class CodeVisionGroupDefaultSettingModel(override val name: String,
                                              private val groupId: String,
                                              override val description: String?,
                                              override var isEnabled: Boolean,
                                              val providers: List<CodeVisionProvider<*>>) : CodeVisionGroupSettingModel {

  private val settings = CodeVisionSettings.instance()
  private lateinit var positionComboBox: ComboBox<CodeVisionAnchorKind>


  override val component: JComponent = panel {
    row {
      label(CodeVisionBundle.message("CodeVisionConfigurable.column.name.position"))
      positionComboBox = comboBox(CodeVisionGlobalSettingsProvider.supportedAnchors).component
    }
  }

  override fun isModified(): Boolean {
    return (isEnabled != (settings.isProviderEnabled(groupId) && settings.codeVisionEnabled)
            || positionComboBox.item != (settings.getPositionForGroup(name) ?: settings.defaultPosition))
  }

  override fun apply() {
    settings.setProviderEnabled(groupId, isEnabled)
    settings.setPositionForGroup(name, positionComboBox.item)
  }

  override fun reset() {
    isEnabled = settings.isProviderEnabled(groupId) && settings.codeVisionEnabled
    positionComboBox.item = settings.getPositionForGroup(name) ?: CodeVisionAnchorKind.Default
  }
}
