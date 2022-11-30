// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.hints.codeVision.CodeVisionPass
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderAdapter
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ResourceUtil
import javax.swing.JComponent
import javax.swing.JPanel

open class CodeVisionGroupDefaultSettingModel(override val name: String,
                                              groupId: String,
                                              override val description: String?,
                                              isEnabled: Boolean,
                                              val providers: List<CodeVisionProvider<*>>) : CodeVisionGroupSettingModel(isEnabled, id = groupId) {
  companion object {
    private val CODE_VISION_PREVIEW_ENABLED = Key<Boolean>("code.vision.preview.data")

    internal fun isEnabledInPreview(editor: Editor) : Boolean? {
      return editor.getUserData(CODE_VISION_PREVIEW_ENABLED)
    }

    internal val anchorRenderer: SimpleListCellRenderer<CodeVisionAnchorKind> = SimpleListCellRenderer.create(
      SimpleListCellRenderer.Customizer { label, value, _ -> label.text = CodeVisionBundle.message(value.key) })
  }


  private val settings = CodeVisionSettings.instance()
  private val uiData by lazy {
    var positionComboBox: ComboBox<CodeVisionAnchorKind>? = null
    val panel = panel {
      row {
        label(CodeVisionBundle.message("CodeVisionConfigurable.column.name.position"))
        val comboBox = comboBox(CodeVisionGlobalSettingsProvider.supportedAnchors, anchorRenderer).component
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

  override val previewLanguage: Language?
    get() {
      val primaryIdeLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
      return CodeVisionSettingsPreviewLanguage.EP_NAME.extensionList.asSequence()
               .filter { it.modelId == id }
               .map { Language.findLanguageByID(it.language) }
               .sortedBy { primaryIdeLanguages.indexOf(it).takeIf { it != -1 } ?: Integer.MAX_VALUE }
               .firstOrNull()
             ?: Language.findLanguageByID("JAVA")
    }

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
