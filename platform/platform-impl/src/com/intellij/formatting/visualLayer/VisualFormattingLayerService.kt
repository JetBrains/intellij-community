// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.RegistryManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes


private const val REGISTRY_KEY = "editor.visual.formatting.layer.enabled"
val visualFormattingElementKey = Key.create<Boolean>("visual.formatting.element")

abstract class VisualFormattingLayerService {

  val editorFactory: EditorFactory by lazy { EditorFactory.getInstance() }

  val enabledByRegistry: Boolean
    get() = RegistryManager.getInstance().`is`(REGISTRY_KEY)

  val enabledBySettings: Boolean
    get() = UISettings.getInstance().showVisualFormattingLayer

  var enabledGlobally: Boolean = false

  var scheme: CodeStyleScheme
    get() = with(CodeStyleSchemes.getInstance()) {
      allSchemes.find { it.isUsedForVisualFormatting } ?: defaultScheme
    }
    set(scheme) = with(CodeStyleSchemes.getInstance()) {
      allSchemes.forEach { it.isUsedForVisualFormatting = false }
      scheme.isUsedForVisualFormatting = true
    }

  fun getSchemes(): List<CodeStyleScheme> = CodeStyleSchemes.getInstance().allSchemes

  val disabledGlobally: Boolean
    get() = !enabledGlobally

  fun enabledForEditor(editor: Editor) =
    editor.settings.isShowVisualFormattingLayer ?: enabledGlobally

  fun disabledForEditor(editor: Editor) =
    !enabledForEditor(editor)

  fun enableForEditor(editor: Editor) {
    if (disabledForEditor(editor)) {
      if (enabledGlobally) {
        editor.settings.isShowVisualFormattingLayer = null
      }
      else {
        editor.settings.isShowVisualFormattingLayer = true
      }
    }
  }

  fun disableForEditor(editor: Editor) {
    if (enabledForEditor(editor)) {
      if (disabledGlobally) {
        editor.settings.isShowVisualFormattingLayer = null
      }
      else {
        editor.settings.isShowVisualFormattingLayer = false
      }
    }
  }

  //------------------------------
  // Global stuff
  fun enableGlobally() {
    editorFactory.allEditors.forEach { it.settings.isShowVisualFormattingLayer = null }
    enabledGlobally = true
  }

  fun disableGlobally() {
    editorFactory.allEditors.forEach { it.settings.isShowVisualFormattingLayer = null }
    enabledGlobally = false
  }
  //------------------------------


  abstract fun getVisualFormattingLayerElements(file: PsiFile): List<VisualFormattingLayerElement>

  companion object {
    @JvmStatic
    fun getInstance(): VisualFormattingLayerService =
      ApplicationManager.getApplication().getService(VisualFormattingLayerService::class.java)
  }

}

sealed class VisualFormattingLayerElement {

  abstract fun applyToEditor(editor: Editor): Unit

  data class InlineInlay(val offset: Int, val length: Int) : VisualFormattingLayerElement() {
    override fun applyToEditor(editor: Editor) {
      editor.inlayModel
        .addInlineElement(
          offset,
          false,
          InlayPresentation(editor, length)
        )
    }
  }

  data class BlockInlay(val offset: Int, val lines: Int) : VisualFormattingLayerElement() {
    override fun applyToEditor(editor: Editor) {
      editor.inlayModel
        .addBlockElement(
          offset,
          true,
          true,
          0,
          InlayPresentation(editor, lines, vertical = true)
        )
    }
  }

  data class Folding(val offset: Int, val length: Int) : VisualFormattingLayerElement() {
    override fun applyToEditor(editor: Editor) {
      editor.foldingModel.runBatchFoldingOperation {
        editor.foldingModel
          .addFoldRegion(offset, offset + length, "")
          ?.apply {
            isExpanded = false
            shouldNeverExpand()
            putUserData(visualFormattingElementKey, true)
          }
      }
    }
  }
}


data class InlayPresentation(val editor: Editor,
                             val fillerLength: Int,
                             val vertical: Boolean = false) : EditorCustomElementRenderer {

  private val editorFontMetrics by lazy {
    val editorFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    editor.contentComponent.getFontMetrics(editorFont)
  }

  override fun calcWidthInPixels(inlay: Inlay<*>) =
    if (vertical) 0 else editorFontMetrics.stringWidth(" ".repeat(fillerLength))

  override fun calcHeightInPixels(inlay: Inlay<*>) =
    (if (vertical) fillerLength else 1) * editorFontMetrics.height


}
