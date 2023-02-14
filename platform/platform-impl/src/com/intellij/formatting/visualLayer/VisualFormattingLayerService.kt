// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Key
import com.intellij.psi.codeStyle.CodeStyleSettings

val visualFormattingElementKey = Key.create<Boolean>("visual.formatting.element")

abstract class VisualFormattingLayerService {
  private val EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS = Key.create<CodeStyleSettings>("visual.formatting.layer.info")
  private val Editor.visualFormattingLayerEnabled: Boolean
    get() = visualFormattingLayerCodeStyleSettings != null
  var Editor.visualFormattingLayerCodeStyleSettings: CodeStyleSettings?
    get() = getUserData(EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS)
    private set(value) = putUserData(EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS, value)

  fun enabledForEditor(editor: Editor) = editor.visualFormattingLayerEnabled

  fun enableForEditor(editor: Editor, codeStyleSettings: CodeStyleSettings) {
    editor.visualFormattingLayerCodeStyleSettings = codeStyleSettings
  }

  fun disableForEditor(editor: Editor) {
    editor.visualFormattingLayerCodeStyleSettings = null
  }

  abstract fun collectVisualFormattingLayerElements(editor: Editor): List<VisualFormattingLayerElement>

  abstract fun applyVisualFormattingLayerElementsToEditor(editor: Editor, elements: List<VisualFormattingLayerElement>)

  companion object {
    @JvmStatic
    fun getInstance(): VisualFormattingLayerService =
      ApplicationManager.getApplication().getService(VisualFormattingLayerService::class.java)
  }

}

sealed class VisualFormattingLayerElement {

  abstract fun applyToEditor(editor: Editor)

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
      (editor.foldingModel as? FoldingModelEx)
        ?.createFoldRegion(offset, offset + length, "", null, true)
        ?.apply {
          putUserData(visualFormattingElementKey, true)
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
