// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.hints.codeVision.CodeVisionPass
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderAdapter
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ResourceUtil
import javax.swing.JComponent
import javax.swing.JPanel

open class CodeVisionGroupDefaultSettingModel(override val name: String,
                                              groupId: String,
                                              override val description: String?,
                                              override val previewLanguage: Language?,
                                              isEnabled: Boolean,
                                              val providers: List<CodeVisionProvider<*>>) : CodeVisionGroupSettingModel(isEnabled, id = groupId) {

  constructor(name: String,
              groupId: String,
              description: String?,
              isEnabled: Boolean,
              providers: List<CodeVisionProvider<*>>) : this(name, groupId, description, null, isEnabled, providers)

  companion object {
    private val CODE_VISION_PREVIEW_ENABLED = Key<Boolean>("code.vision.preview.data")

    internal fun isEnabledInPreview(editor: Editor) : Boolean? {
      return editor.getUserData(CODE_VISION_PREVIEW_ENABLED)
    }
  }


  private val settings = CodeVisionSettings.getInstance()
  private val uiData by lazy {
    var positionComboBox: ComboBox<CodeVisionAnchorKind>? = null
    val panel = panel {
      row {
        label(CodeVisionMessageBundle.message("CodeVisionConfigurable.column.name.position"))
        val comboBox = comboBox(CodeVisionGlobalSettingsProvider.supportedAnchors, renderer = textListCellRenderer("") {
          CodeVisionMessageBundle.message(it.key)
        }).component
        comboBox.item = settings.getPositionForGroup(id)
        positionComboBox = comboBox
      }
    }
    UIData(panel, positionComboBox!!)
  }

  override fun collectData(editor: Editor, file: PsiFile): Runnable {
    for (provider in providers) {
      provider.preparePreview(editor, file)
    }
    val daemonBoundProviders = providers.filterIsInstance<CodeVisionProviderAdapter>().map { it.delegate }
    val codeVisionData = CodeVisionPass.collectData(editor, file, daemonBoundProviders)
    return Runnable {
      editor.putUserData(CODE_VISION_PREVIEW_ENABLED, isEnabled)
      val project = editor.project ?: return@Runnable
      codeVisionData.applyTo(editor, project)
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal
        .fire(CodeVisionHost.LensInvalidateSignal(editor))
    }
  }

  override val component: JComponent
    get() = uiData.component


  override val previewText: String?
    get() = getCasePreview()

  override fun isModified(): Boolean {
    return (isEnabled != (settings.isProviderEnabled(id) && settings.codeVisionEnabled)
            || (uiData.positionComboBox.item != null && uiData.positionComboBox.item != getPositionForGroup()))
  }

  private fun getPositionForGroup() = settings.getPositionForGroup(id) ?: CodeVisionAnchorKind.Default

  override fun apply() {
    settings.setProviderEnabled(id, isEnabled)
    settings.setPositionForGroup(id, uiData.positionComboBox.item)
  }

  override fun reset() {
    isEnabled = settings.isProviderEnabled(id) && settings.codeVisionEnabled
    uiData.positionComboBox.item = settings.getPositionForGroup(id) ?: CodeVisionAnchorKind.Default
  }

  private fun getCasePreview(): String? {
    val associatedFileType = previewLanguage?.associatedFileType ?: return null
    val path = "codeVisionProviders/" + id + "/preview." + associatedFileType.defaultExtension
    val stream = associatedFileType.javaClass.classLoader.getResourceAsStream(path)
    return if (stream != null) ResourceUtil.loadText(stream) else null
  }

  private class UIData(val component: JPanel, val positionComboBox: ComboBox<CodeVisionAnchorKind>)
}
