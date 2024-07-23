// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComponent.Companion.LOADING_PREVIEW
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.psi.PsiFile
import com.intellij.ui.ScreenUtil
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.popup.PopupPositionManager.Position.LEFT
import com.intellij.ui.popup.PopupPositionManager.Position.RIGHT
import com.intellij.ui.popup.PopupPositionManager.PositionAdjuster
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.CancellablePromise
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import javax.swing.JComponent
import javax.swing.JWindow
import kotlin.math.max
import kotlin.math.min

class IntentionPreviewPopupUpdateProcessor internal constructor(
  private val project: Project, private val fn: (Any?) -> IntentionPreviewInfo,
) : PopupUpdateProcessor(project) {
  private var index: Int = LOADING_PREVIEW
  private var show = false
  private var originalPopup: JBPopup? = null
  private val editorsToRelease = mutableListOf<EditorEx>()
  private var promise: CancellablePromise<IntentionPreviewInfo?>? = null

  private lateinit var popup: JBPopup
  private lateinit var component: IntentionPreviewComponent
  private var justActivated: Boolean = false

  private fun getPopupWindow(): JWindow? = UIUtil.getParentOfType(JWindow::class.java, popup.content)

  override fun updatePopup(intentionAction: Any?) {
    if (!show) {
      return
    }

    if (!::popup.isInitialized || popup.isDisposed) {
      val origPopup = originalPopup
      if (origPopup == null || origPopup.isDisposed) return
      component = IntentionPreviewComponent(origPopup)

      component.multiPanel.select(LOADING_PREVIEW, true)

      var popupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
        .setCancelCallback { cancel() }
        .setCancelKeyEnabled(false)
        .setShowBorder(false)
        .addUserData(IntentionPreviewPopupKey())

      //see with com.intellij.ui.popup.AbstractPopup.show(java.awt.Component, int, int, boolean).
      //don't use in cases when borders may be preserved
      if (WindowRoundedCornersManager.isAvailable() && SystemInfoRt.isMac && UIUtil.isUnderDarcula()) {
        popupBuilder = popupBuilder.setShowBorder(true)
      }

      popup = popupBuilder.createPopup()

      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          var size = popup.size
          size = Dimension(size.width.coerceAtLeast(MIN_WIDTH), size.height)
          popup.content.preferredSize = size
          popup.size = size
          adjustPosition(originalPopup, true)
        }
      })
      adjustPosition(originalPopup)
      addMoveListener(originalPopup) { adjustPosition(originalPopup) }
    }

    val value = component.multiPanel.getValue(index, false)
    if (value != null) {
      promise?.cancel()
      select(index)
      return
    }

    component.startLoading()

    promise = ReadAction.nonBlocking<IntentionPreviewInfo> { postprocess(fn(intentionAction)) }
      .expireWith(popup)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.defaultModalityState()) { select(index, renderPreview(it)) }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun addMoveListener(popup: JBPopup?, action: () -> Unit) {
    if (popup == null) {
      return
    }

    popup.content.addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
      override fun ancestorMoved(e: HierarchyEvent?) {
        action.invoke()
      }
    })
  }

  private fun adjustPosition(originalPopup: JBPopup?, checkResizing: Boolean = false) {
    if (popup.isDisposed || originalPopup == null || !originalPopup.content.isShowing) {
      return
    }

    val positionAdjuster = PositionAdjuster(originalPopup.content)
    val previousDimension = PopupImplUtil.getPopupSize(popup)
    val bounds: Rectangle = positionAdjuster.adjustBounds(previousDimension, arrayOf(RIGHT, LEFT))
    val popupSize = popup.size
    val screen = ScreenUtil.getScreenRectangle(bounds.x, bounds.y)
    val targetBounds = Rectangle(Point(bounds.x, bounds.y), popup.content.preferredSize)
    if (targetBounds.width > screen.width || targetBounds.height > screen.height) {
      hide()
    }
    if (checkResizing && popupSize != null && bounds.width < MIN_WIDTH) {
      hide()
    }
    else {
      positionAdjuster.adjust(popup, previousDimension, bounds)
    }
  }

  private fun renderPreview(result: IntentionPreviewInfo): JComponent {
    return when (result) {
      is IntentionPreviewDiffResult -> {
        val editors = IntentionPreviewEditorsPanel.createEditors(project, result)
        if (editors.isEmpty()) {
          IntentionPreviewComponent.NO_PREVIEW_LABEL
        }
        else {
          val size = component.preferredSize
          val location = popup.locationOnScreen
          val screen = ScreenUtil.getScreenRectangle(location)

          var delta = screen.width + screen.x - location.x
          val content = originalPopup?.content
          val origLocation = if (content?.isShowing == true) content.locationOnScreen else null
          // On the left side of the original popup: avoid overlap
          if (origLocation != null && location.x < origLocation.x) {
            delta = delta.coerceAtMost(origLocation.x - screen.x - PositionAdjuster.DEFAULT_GAP)
          }
          size.width = size.width.coerceAtMost(delta)

          for (editor in editors) {
            editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
              override fun recalculationEnds() {
                val height = (editor as EditorImpl).offsetToXY(editor.document.textLength).y + editor.lineHeight + 6
                editor.component.preferredSize = Dimension(editor.component.preferredSize.width, min(height, MAX_HEIGHT))
                editor.component.parent?.invalidate()
                popup.pack(true, true)
              }

              override fun softWrapsChanged() {}
            })

            editor.component.preferredSize = Dimension(max(size.width, MIN_WIDTH), min(editor.component.preferredSize.height, MAX_HEIGHT))
          }

          editorsToRelease.addAll(editors)
          IntentionPreviewEditorsPanel(editors.toMutableList())
        }
      }
      is Html -> IntentionPreviewComponent.createHtmlPanel(result)
      else -> IntentionPreviewComponent.NO_PREVIEW_LABEL
    }
  }

  fun setup(popup: JBPopup, parentIndex: Int) {
    index = parentIndex
    originalPopup = popup
  }

  fun isShown(): Boolean = show && getPopupWindow()?.isVisible != false

  fun hide() {
    if (::popup.isInitialized && !popup.isDisposed) {
      popup.cancel()
    }
  }

  fun show() {
    show = true
  }

  private fun cancel(): Boolean {
    promise?.cancel()

    if (editorsToRelease.isNotEmpty()) {
      val editorFactory = EditorFactory.getInstance()
      for (editor in editorsToRelease) {
        editorFactory.releaseEditor(editor)
      }
      editorsToRelease.clear()
    }
    component.removeAll()
    show = false
    return true
  }

  private fun select(index: Int, previewComponent: JComponent? = null) {
    val selectedComponent = previewComponent ?: component.multiPanel.getValue(index, false)
    getPopupWindow()?.isVisible = selectedComponent != IntentionPreviewComponent.NO_PREVIEW_LABEL || justActivated
    justActivated = false
    component.stopLoading()
    // need to set previewComponent before select, as multiPanel.create expects previewComponent to be initialized
    component.previewComponent = previewComponent
    component.multiPanel.select(index, true)

    popup.pack(true, true)
  }

  /**
   * Call when the process is just activated via hotkey
   */
  fun activate() {
    justActivated = true
  }

  companion object {
    internal const val MAX_HEIGHT: Int = 300
    internal const val MIN_WIDTH: Int = 300

    fun getShortcutText(): String = KeymapUtil.getPreferredShortcutText(getShortcutSet().shortcuts)
    fun getShortcutSet(): ShortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_QUICK_JAVADOC)

    @TestOnly
    @JvmStatic
    fun getPreviewText(
      project: Project,
      action: IntentionAction,
      originalFile: PsiFile,
      originalEditor: Editor,
    ): String? {
      return (getPreviewInfo(project, action, originalFile, originalEditor) as? IntentionPreviewDiffResult)?.newText
    }

    /**
     * Returns content of preview:
     * if it's diff then new content is returned
     * if it's HTML then text representation is returned
     */
    @TestOnly
    @JvmStatic
    fun getPreviewContent(
      project: Project,
      action: IntentionAction,
      originalFile: PsiFile,
      originalEditor: Editor,
    ): String {
      return when (val info = getPreviewInfo(project, action, originalFile, originalEditor)) {
        is IntentionPreviewDiffResult -> info.newText
        is Html -> info.content().toString()
        else -> ""
      }
    }

    private fun postprocess(info: IntentionPreviewInfo) = when (info) {
      is IntentionPreviewInfo.CustomDiff -> IntentionPreviewDiffResult.fromCustomDiff(info)
      is IntentionPreviewInfo.MultiFileDiff -> IntentionPreviewDiffResult.fromMultiDiff(info)
      else -> info
    }

    @TestOnly
    @JvmStatic
    @JvmOverloads
    fun getPreviewInfo(
      project: Project,
      action: IntentionAction,
      originalFile: PsiFile,
      originalEditor: Editor,
      fixOffset: Int = -1,
    ): IntentionPreviewInfo =
      postprocess(ProgressManager.getInstance().runProcess<IntentionPreviewInfo>(
        {
          IntentionPreviewComputable(
            project = project,
            action = action,
            originalFile = originalFile,
            originalEditor = originalEditor,
            fixOffset = fixOffset,
          ).generatePreview()
        },
        EmptyProgressIndicator()) ?: IntentionPreviewInfo.EMPTY)
  }

  internal class IntentionPreviewPopupKey
}