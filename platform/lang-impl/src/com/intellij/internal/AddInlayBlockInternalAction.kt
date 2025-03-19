// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ColorPicker
import com.intellij.ui.ColorUtil
import com.intellij.ui.colorpicker.ColorButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.math.max

@ApiStatus.Internal
class AddInlayBlockInternalAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = (e.getData(CommonDataKeys.EDITOR) as? EditorImpl) ?: return

    val carets = editor.caretModel.getAllCarets()
    if (carets.isEmpty()) return

    val caret = carets.first() ?: return
    val offset = caret.offset

    val dialog = Dialog(project)
    if (!dialog.showAndGet()) return

    val properties = InlayProperties().showAbove(dialog.showAbove)
    val renderer = BlockInlayRenderer(editor, dialog.height, dialog.blockColor, dialog.fill)

    editor.inlayModel.addBlockElement(offset, properties, renderer)
    // TODO: add ability to remove inlay
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = (e.getData(CommonDataKeys.EDITOR) as? EditorImpl)
    e.presentation.setEnabled(project != null && editor != null)
  }

  private class Dialog(project: Project) : DialogWrapper(project) {
    var height: Int = 50
    var showAbove: Boolean = true
    var fill: Boolean = true
    val blockColor: Color
      get() = colorButton.color

    @Suppress("UseJBColor")
    private val DEFAULT_COLOR = Color(0xC102FF)
    private val colorButton: ColorButton = ColorButton(DEFAULT_COLOR).apply {
      addActionListener {
        val newColor = ColorPicker.showDialog(this, "Block Color", DEFAULT_COLOR, false, emptyList(), false)
        this.color = newColor ?: DEFAULT_COLOR
      }
    }

    init {
      title = "Add Block Inlay"
      init()
    }

    override fun createCenterPanel(): JComponent {
      return panel {
        row("Height:") {
          intTextField(IntRange(1, 10000), 10)
            .bindIntText(::height)
            .focused()
            .align(AlignX.FILL)
        }
        row("Color:") {
          cell(colorButton)

          checkBox("Fill")
            .bindSelected(::fill)
        }
        row {
          checkBox("Show above")
            .bindSelected(::showAbove)
        }
      }
    }
  }

  private class BlockInlayRenderer(
    private val editor: EditorEx,
    val height: Int,
    private val inlayColor: Color?,
    private val fill: Boolean
  ) : EditorCustomElementRenderer {
    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      inlayColor ?: return
      if (fill) {
        g.color = ColorUtil.toAlpha(inlayColor, 100)
        g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
      }
      g.color = inlayColor
      g.drawRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = max((editor as EditorImpl).preferredSize.width, editor.component.width)
    override fun calcHeightInPixels(inlay: Inlay<*>): Int = height

    override fun calcGutterIconRenderer(inlay: Inlay<*>): GutterIconRenderer? {
      return super.calcGutterIconRenderer(inlay)
    }
  }
}