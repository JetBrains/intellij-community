// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.formatting.visualLayer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

val visualFormattingElementKey: Key<Boolean> = Key.create("visual.formatting.element")

private val EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS = Key.create<CodeStyleSettings>("visual.formatting.layer.info")

/**
 * This is an interface that might be used by the non-platform virtual formatting providers
 * to make friends with editor features that rely on measuring whitespace to display correctly,
 * such as indent guides or rendered docs.
 */
interface VirtualFormattingInlaysInfo {

  companion object {
    @JvmStatic
    fun getInstance(): VirtualFormattingInlaysInfo = application.service<VirtualFormattingInlaysInfo>()

    @JvmStatic
    fun measureVirtualFormattingInlineInlays(editor: Editor, startOffset: Int, endOffset: Int): Int {
      val instance = getInstance()
      return instance.measureVirtualFormattingInlineInlays(editor, startOffset, endOffset)
    }

    @JvmStatic
    fun VirtualFormattingInlaysInfo.measureVirtualFormattingInlineInlays(editor: Editor, startOffset: Int, endOffset: Int): Int {
      if (isVirtualFormattingEnabled(editor)) return 0
      return getVisualFormattingInlineInlays(editor, startOffset, endOffset)
        .sumOf { it.renderer.calcWidthInPixels(it) }
    }
  }

  fun isVirtualFormattingEnabled(editor: Editor): Boolean

  fun getVisualFormattingInlineInlays(editor: Editor, startOffset: Int, endOffset: Int): List<Inlay<*>>
}

@ApiStatus.Internal
class PlatformVirtualFormattingInlaysInfo : VirtualFormattingInlaysInfo {
  override fun isVirtualFormattingEnabled(editor: Editor): Boolean {
    return VisualFormattingLayerService.isEnabledForEditor(editor)
  }

  override fun getVisualFormattingInlineInlays(editor: Editor, startOffset: Int, endOffset: Int): List<Inlay<*>> {
    return VisualFormattingLayerService.getVisualFormattingInlineInlays(editor, startOffset, endOffset)
  }
}

abstract class VisualFormattingLayerService {

  abstract fun collectVisualFormattingLayerElements(editor: Editor): List<VisualFormattingLayerElement>

  abstract fun applyVisualFormattingLayerElementsToEditor(editor: Editor, elements: List<VisualFormattingLayerElement>)

  companion object {
    private const val removeZombieFoldingsRegistryKey = "editor.readerMode.vfmt.removeZombies"

    @JvmStatic
    fun shouldRemoveZombieFoldings(): Boolean = Registry.`is`(removeZombieFoldingsRegistryKey)

    @JvmStatic
    fun getInstance(): VisualFormattingLayerService =
      ApplicationManager.getApplication().getService(VisualFormattingLayerService::class.java)

    private val Editor.visualFormattingLayerEnabled: Boolean
      get() = visualFormattingLayerCodeStyleSettings != null
    var Editor.visualFormattingLayerCodeStyleSettings: CodeStyleSettings?
      get() = getUserData(EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS)
      private set(value) = putUserData(EDITOR_VISUAL_FORMATTING_LAYER_CODE_STYLE_SETTINGS, value)

    @JvmStatic
    fun isEnabledForEditor(editor: Editor): Boolean = editor.visualFormattingLayerEnabled

    @JvmStatic
    fun enableForEditor(editor: Editor, codeStyleSettings: CodeStyleSettings) {
      editor.visualFormattingLayerCodeStyleSettings = codeStyleSettings
    }

    @JvmStatic
    fun disableForEditor(editor: Editor) {
      editor.visualFormattingLayerCodeStyleSettings = null
    }

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun getVisualFormattingInlineInlays(editor: Editor, startOffset: Int, endOffset: Int): List<Inlay<out InlayPresentation>> =
      editor.inlayModel
        .getInlineElementsInRange(startOffset, endOffset)
        .filter { it.renderer.let { it is InlayPresentation && !it.vertical } }
        as List<Inlay<out InlayPresentation>>
  }

}

@ApiStatus.Internal
sealed class VisualFormattingLayerElement {

  abstract fun applyToEditor(editor: Editor)

  data class InlineInlay(val offset: Int, val length: Int) : VisualFormattingLayerElement() {
    override fun applyToEditor(editor: Editor) {
      editor.inlayModel
        .addInlineElement(
          offset,
          false,
          // Visual formatting inlays should always be displayed as part of the preceding whitespace
          Integer.MAX_VALUE,
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

@ApiStatus.Internal
data class InlayPresentation(val editor: Editor,
                             val fillerLength: Int,
                             val vertical: Boolean = false) : EditorCustomElementRenderer {

  private val editorFontMetrics by lazy {
    val editorFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    editor.contentComponent.getFontMetrics(editorFont)
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int =
    if (vertical) 0 else editorFontMetrics.stringWidth(" ".repeat(fillerLength))

  override fun calcHeightInPixels(inlay: Inlay<*>): Int =
    (if (vertical) fillerLength else 1) * editorFontMetrics.height

}
