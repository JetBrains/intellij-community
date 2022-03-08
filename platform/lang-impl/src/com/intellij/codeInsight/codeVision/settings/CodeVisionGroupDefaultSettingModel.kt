// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionBundle
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.hints.codeVision.CodeVisionPass
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderAdapter
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ResourceUtil
import javax.swing.JComponent

open class CodeVisionGroupDefaultSettingModel(override val name: String,
                                              groupId: String,
                                              override val description: String?,
                                              isEnabled: Boolean,
                                              val providers: List<CodeVisionProvider<*>>) : CodeVisionGroupSettingModel(isEnabled, id = groupId) {

  private val settings = CodeVisionSettings.instance()
  private lateinit var positionComboBox: ComboBox<CodeVisionAnchorKind>

  override fun collectData(editor: Editor, file: PsiFile): Runnable {
    val visionProviders = providers.filterIsInstance<CodeVisionProviderAdapter>().map { it.delegate }
    val codeVisionData = CodeVisionPass.collectData(editor, file, visionProviders)
    return Runnable {
      val project = editor.project ?: return@Runnable
      codeVisionData.applyTo(editor, project)
    }
  }

  override val component: JComponent = panel {
    row {
      label(CodeVisionBundle.message("CodeVisionConfigurable.column.name.position"))
      positionComboBox = comboBox(CodeVisionGlobalSettingsProvider.supportedAnchors).component
    }
  }

  override val previewText: String?
    get() = getCasePreview()

  override val previewLanguage: Language?
    get() = Language.findLanguageByID("JAVA")

  override fun isModified(): Boolean {
    return (isEnabled != (settings.isProviderEnabled(id) && settings.codeVisionEnabled)
            || positionComboBox.item != (settings.getPositionForGroup(name) ?: settings.defaultPosition))
  }

  override fun apply() {
    settings.setProviderEnabled(id, isEnabled)
    settings.setPositionForGroup(name, positionComboBox.item)
  }

  override fun reset() {
    isEnabled = settings.isProviderEnabled(id) && settings.codeVisionEnabled
    positionComboBox.item = settings.getPositionForGroup(name) ?: CodeVisionAnchorKind.Default
  }

  private fun getCasePreview(): String? {
    val path = "codeVisionProviders/" + id + "/preview." + previewLanguage?.associatedFileType?.defaultExtension
    val stream = this.javaClass.classLoader.getResourceAsStream(path)
    return if (stream != null) ResourceUtil.loadText(stream) else null
  }
}
