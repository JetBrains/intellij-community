// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.PopupBorder
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.*
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

private class ShowDemoAltClickPromoterAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    SampleDialogWrapper(e.project ?: return).showAndGet()
  }
}

@Suppress("HardCodedStringLiteral")
private class SampleDialogWrapper(val project: Project) : DialogWrapper(true) {
  init {
    @Suppress("DialogTitleCapitalization")
    title = "Alt+Click demo promoter"
    init()
  }

  val promoHeight get() = 200

  lateinit var editor: EditorImpl
  lateinit var panelWithAnimation: PanelWithAnimation
  lateinit var dialogPane: JBLayeredPane

  override fun beforeShowCallback() {
    val animator = panelWithAnimation

    val fragment1 = "foo()"
    val (indexOfFoo, target1) = targetPointByOffset(fragment1, 2)
    val fragment2 = "foo().calc()"
    val (_, target2) = targetPointByOffset(fragment2, fragment1.length)
    val (_, target3) = targetPointByOffset(fragment2, 8)

    var length = 500

    val job = AltClickServiceForCoroutine.getInstance().coroutineScope.launch(Dispatchers.EDT + ModalityState.defaultModalityState().asContextElement()) {
      while (true) {
        animator.cursorIcon = AllIcons.Ide.Rating
        moveCursor(length, animator, Point(100, 100), target1)

        var highlighter = addHighlightToFragment(indexOfFoo, fragment1)
        animator.cursorIcon = AllIcons.Actions.Upload
        dialogPane.repaint()
        delay(200)

        panelWithAnimation.add(NonOpaquePanel().also {
          val jBHtmlPane = JBHtmlPane(QuickDocHighlightingHelper.getDefaultDocStyleOptions(editor.colorsScheme, true), JBHtmlPaneConfiguration.builder().build()).also {
            it.background = editor.backgroundColor
            it.text = "<shortcut raw=\"Alt\"/>"
          }
          it.border = JBUI.Borders.empty(promoHeight - jBHtmlPane.getFontMetrics(jBHtmlPane.font).height - 6, 40, 0, 0)
          it.alignmentX = Component.LEFT_ALIGNMENT
          it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
          it.add(jBHtmlPane)
        })

        dialogPane.revalidate()
        dialogPane.repaint()

        moveCursor(length, animator, target1, target2)
        highlighter.dispose()
        highlighter = addHighlightToFragment(indexOfFoo, fragment2)

        dialogPane.repaint()
        moveCursor(length, animator, target2, target3)
        dialogPane.repaint()

        val evalPanel = JBUI.Panels.simplePanel()

        evalPanel.border = PopupBorder.Factory.create(true, true)
        val textViewer = DebuggerUIUtil.createTextViewer("Mega 42", project)
        evalPanel.background = textViewer.background

        evalPanel.add(NonOpaquePanel().also {
          it.border = JBUI.Borders.empty(10)
          it.add(textViewer)
        })

        val evalHeight = 50
        evalPanel.maximumSize = Dimension(100, evalHeight)
        val evalY = target3.y + 20

        val evalPart = NonOpaquePanel().also {
          it.border = JBUI.Borders.empty(evalY, target3.x + 20, 0, 0)
          it.alignmentX = Component.LEFT_ALIGNMENT
          it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
          it.add(evalPanel)
        }

        panelWithAnimation.removeAll()
        panelWithAnimation.add(evalPart)

        panelWithAnimation.add(NonOpaquePanel().also {
          val jBHtmlPane = JBHtmlPane(QuickDocHighlightingHelper.getDefaultDocStyleOptions(editor.colorsScheme, true), JBHtmlPaneConfiguration.builder().build()).also {
            it.background = editor.backgroundColor
            it.text = "<shortcut raw=\"Alt + Click\"/>"
          }
          it.border = JBUI.Borders.empty(promoHeight - evalHeight - evalY - jBHtmlPane.getFontMetrics(jBHtmlPane.font).height, 40, 0, 0)
          it.alignmentX = Component.LEFT_ALIGNMENT
          it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
          it.add(jBHtmlPane)
        })

        dialogPane.revalidate()
        dialogPane.repaint()

        delay(2000)

        panelWithAnimation.removeAll()
        highlighter.dispose()
      }
    }
    job.cancelOnDispose(disposable)
  }

  private fun addHighlightToFragment(indexOfFoo: Int, fragment1: String): RangeHighlighter {
    val attributes = editor.colorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
    val highlighter = editor.markupModel.addRangeHighlighter(indexOfFoo, indexOfFoo + fragment1.length,
                                                             HighlighterLayer.SELECTION, attributes,
                                                             HighlighterTargetArea.EXACT_RANGE)
    return highlighter
  }

  private fun targetPointByOffset(fragment: String, shift: Int): Pair<Int, Point> {
    val indexOfFoo = editor.document.charsSequence.indexOf(fragment)
    val pointOfSymbol = editor.offsetToPoint2D(indexOfFoo + shift)
    val targetInEditor = Point(pointOfSymbol.x.toInt(), pointOfSymbol.y.toInt() + editor.charHeight / 2)
    val target = SwingUtilities.convertPoint(editor.contentComponent, targetInEditor, panelWithAnimation)
    return Pair(indexOfFoo, target)
  }

  private suspend fun moveCursor(
    length: Int,
    animator: PanelWithAnimation,
    start: Point,
    target: Point,
  ) {
    val startMillis = System.currentTimeMillis()
    for (i in 0 until length step 20) {
      val initialX = start.x
      val initialY = start.y
      val p = Point(initialX + (target.x - initialX) * i / length, initialY + (target.y - initialY) * i / length)
      animator.cursorPosition = p
      dialogPane.repaint()

      val d = (startMillis + i) - System.currentTimeMillis()
      if (d > 0) delay(d)
    }
  }

  override fun createCenterPanel(): JComponent? {

    val editorFactory = EditorFactory.getInstance()
    val document = editorFactory.createDocument("""
      fun boo() {
        foo().calc()
      }
    """.trimIndent())
    editor = editorFactory.createViewer(document) as EditorImpl

    dialogPane = JBLayeredPane()
    dialogPane.isFullOverlayLayout = true

    panelWithAnimation = PanelWithAnimation()
    panelWithAnimation.layout = BoxLayout(panelWithAnimation, BoxLayout.Y_AXIS)

    dialogPane.add(panelWithAnimation)

    val behind = JPanel(BorderLayout())
    dialogPane.add(behind)



    editor.settings.isLineNumbersShown = false

    val fileType = Language.findLanguageByID("kotlin")?.associatedFileType
    if (fileType != null) {
      editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
    }

    for (part in listOf(editor.contentComponent, editor.gutterComponentEx)) {
      for (listener in part.mouseListeners.toList()) {
        part.removeMouseListener(listener)
      }
      for (listener in part.mouseMotionListeners.toList()) {
        part.removeMouseMotionListener(listener)
      }

      //part.cursor = Cursor.getDefaultCursor()
      part.isFocusable = false
    }

    val predefinedCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    editor.contentComponent.cursor = predefinedCursor


    behind.add(editor.component, BorderLayout.CENTER)
    editor.component.preferredSize = Dimension(300, promoHeight)


    dialogPane.preferredSize = Dimension(300, promoHeight)
    panelWithAnimation.preferredSize = Dimension(300, promoHeight)
    return dialogPane
  }
}

private class PanelWithAnimation() : NonOpaquePanel() {
  var cursorPosition: Point? = null
  var cursorIcon = AllIcons.Ide.Rating

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    val config = GraphicsUtil.setupAAPainting(g)
    g as? Graphics2D ?: return
    val position = cursorPosition ?: return
    cursorIcon.paintIcon(this, g, position.x, position.y)
    config.restore()
  }
}

@Service
private class AltClickServiceForCoroutine(val coroutineScope: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(): AltClickServiceForCoroutine = service()
  }
}
