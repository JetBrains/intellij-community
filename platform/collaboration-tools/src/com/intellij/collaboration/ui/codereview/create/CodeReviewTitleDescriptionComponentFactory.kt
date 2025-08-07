// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.create

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.SingleComponentCenteringLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

private const val EDITOR_MARGINS = 12
private const val EDITORS_GAP = 8

object CodeReviewTitleDescriptionComponentFactory {

  fun createIn(cs: CoroutineScope, vm: CodeReviewTitleDescriptionViewModel, titleEditor: Editor, descriptionEditor: Editor): JPanel {
    val textPanel = object : JPanel(null) {
      override fun addNotify() {
        super.addNotify()
        InternalDecoratorImpl.componentWithEditorBackgroundAdded(this)
      }

      override fun removeNotify() {
        super.removeNotify()
        InternalDecoratorImpl.componentWithEditorBackgroundRemoved(this)
      }
    }.apply {
      isOpaque = true
      background = JBColor.lazy { EditorColorsManager.getInstance().globalScheme.defaultBackground }
      InternalDecoratorImpl.preventRecursiveBackgroundUpdateOnToolwindow(this)
    }

    cs.launchNow {
      vm.isTemplateLoading.collect {
        with(textPanel) {
          removeAll()
          if (it) {
            layout = SingleComponentCenteringLayout()
            add(LoadingLabel())
          }
          else {
            layout = BorderLayout(0, JBUI.scale(EDITORS_GAP))
            add(titleEditor.component.withMinHeightOfEditorLine(titleEditor), BorderLayout.NORTH)
            add(descriptionEditor.component.withMinHeightOfEditorLine(descriptionEditor, EDITOR_MARGINS), BorderLayout.CENTER)
          }
          revalidate()
          repaint()
        }
      }
    }

    return textPanel
  }

  fun createTitleEditorIn(project: Project, cs: CoroutineScope, vm: CodeReviewTitleDescriptionViewModel, titlePlaceholder: String): Editor {
    return CodeReviewCreateReviewUIUtil.createTitleEditor(project, titlePlaceholder).apply {
      margins = JBUI.insets(EDITOR_MARGINS, EDITOR_MARGINS, 0, EDITOR_MARGINS)
      cs.launchNow {
        try {
          document.bindTextIn(this, vm.titleText, vm::setTitle)
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            EditorFactory.getInstance().releaseEditor(this@apply)
          }
        }
      }
    }
  }

  fun createDescriptionEditorIn(project: Project, cs: CoroutineScope, vm: CodeReviewTitleDescriptionViewModel, descriptionPlaceholder: String): Editor {
    return CodeReviewCreateReviewUIUtil.createDescriptionEditor(project, descriptionPlaceholder).apply {
      margins = JBUI.insets(0, EDITOR_MARGINS)
      cs.launchNow {
        try {
          document.bindTextIn(this, vm.descriptionText, vm::setDescription)
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            EditorFactory.getInstance().releaseEditor(this@apply)
          }
        }
      }
    }
  }
}

private var Editor.margins
  get() = (this as? EditorEx)?.scrollPane?.viewportBorder?.getBorderInsets(scrollPane.viewport) ?: JBUI.emptyInsets()
  set(value) {
    (this as? EditorEx)?.scrollPane?.viewportBorder = JBUI.Borders.empty(value)
  }

private fun Component.withMinHeightOfEditorLine(editor: Editor, additionalGapBottom: Int = 0): JPanel {
  val restrictions = object : DimensionRestrictions {
    override fun getWidth(): Int? = null
    override fun getHeight(): Int = editor.lineHeight +
                                    JBUI.scale(additionalGapBottom) +
                                    editor.insets.run { top + bottom } +
                                    editor.margins.run { top + bottom }
  }
  return withMinSize(restrictions)
}

private fun Component.withMinSize(restrictions: DimensionRestrictions): JPanel {
  val component = this
  return JPanel(null).apply {
    isOpaque = false
    layout = SizeRestrictedSingleComponentLayout().apply {
      minSize = restrictions
    }
    add(component)
  }
}