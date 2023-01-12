// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComponent.Companion.LOADING_PREVIEW
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComponent.Companion.NO_PREVIEW
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
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiFile
import com.intellij.ui.ScreenUtil
import com.intellij.ui.popup.PopupPositionManager.Position.LEFT
import com.intellij.ui.popup.PopupPositionManager.Position.RIGHT
import com.intellij.ui.popup.PopupPositionManager.PositionAdjuster
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JWindow
import kotlin.math.max
import kotlin.math.min

class IntentionPreviewPopupUpdateProcessor(private val project: Project,
                                           private val originalFile: PsiFile,
                                           private val originalEditor: Editor) : PopupUpdateProcessor(project) {
  private var index: Int = LOADING_PREVIEW
  private var show = false
  private var originalPopup : JBPopup? = null
  private val editorsToRelease = mutableListOf<EditorEx>()

  private lateinit var popup: JBPopup
  private lateinit var component: IntentionPreviewComponent
  private var justActivated: Boolean = false
  private val popupWindow: JWindow?
    get() = UIUtil.getParentOfType(JWindow::class.java, popup.content)

  override fun updatePopup(intentionAction: Any?) {
    if (!show) return

    if (!::popup.isInitialized || popup.isDisposed) {
      val origPopup = originalPopup
      if (origPopup == null || origPopup.isDisposed) return
      component = IntentionPreviewComponent(origPopup)

      component.multiPanel.select(LOADING_PREVIEW, true)

      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
        .setCancelCallback { cancel() }
        .setCancelKeyEnabled(false)
        .setShowBorder(false)
        .addUserData(IntentionPreviewPopupKey())
        .createPopup()

      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          var size = popup.size
          val key = component.multiPanel.key
          if (key != NO_PREVIEW) {
            size = Dimension(size.width.coerceAtLeast(MIN_WIDTH), size.height)
          }
          popup.content.preferredSize = size
          adjustPosition(originalPopup)
          popup.size = size
        }
      })
      adjustPosition(originalPopup)
    }

    val value = component.multiPanel.getValue(index, false)
    if (value != null) {
      select(index)
      return
    }

    val action = intentionAction as IntentionAction

    component.startLoading()

    ReadAction.nonBlocking(
      IntentionPreviewComputable(project, action, originalFile, originalEditor))
      .expireWith(popup)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.defaultModalityState()) { renderPreview(it)}
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  private fun adjustPosition(originalPopup: JBPopup?) {
    if (originalPopup != null && originalPopup.content.isShowing) {
      PositionAdjuster(originalPopup.content).adjust(popup, RIGHT, LEFT)
    }
  }

  private fun renderPreview(result: IntentionPreviewInfo) {
    when (result) {
      is IntentionPreviewDiffResult -> {
        val editors = IntentionPreviewModel.createEditors(project, result)
        if (editors.isEmpty()) {
          selectNoPreview()
          return
        }

        editorsToRelease.addAll(editors)
        select(index, editors)
      }
      is Html -> {
        select(index, html = result)
      }
      else -> {
        selectNoPreview()
      }
    }
  }

  private fun selectNoPreview() {
    if (justActivated) {
      select(NO_PREVIEW)
    }
    else {
      popupWindow?.isVisible = false
    }
  }

  fun setup(popup: JBPopup, parentIndex: Int) {
    index = parentIndex
    originalPopup = popup
  }

  fun isShown() = show && popupWindow?.isVisible != false

  fun hide() {
    if (::popup.isInitialized && !popup.isDisposed) {
      popup.cancel()
    }
  }

  fun show() {
    show = true
  }

  private fun cancel(): Boolean {
    editorsToRelease.forEach { editor -> EditorFactory.getInstance().releaseEditor(editor) }
    editorsToRelease.clear()
    component.removeAll()
    show = false
    return true
  }

  private fun select(index: Int, editors: List<EditorEx> = emptyList(), @NlsSafe html: Html? = null) {
    justActivated = false
    popupWindow?.isVisible = true
    component.stopLoading()
    component.editors = editors
    component.html = html
    component.multiPanel.select(index, true)

    val size = component.preferredSize
    val location = popup.locationOnScreen
    val screen = ScreenUtil.getScreenRectangle(location)

    if (screen != null) {
      var delta = screen.width + screen.x - location.x
      val content = originalPopup?.content
      val origLocation = if (content?.isShowing == true) content.locationOnScreen else null
      // On the left side of the original popup: avoid overlap
      if (origLocation != null && location.x < origLocation.x) {
        delta = delta.coerceAtMost(origLocation.x - screen.x - PositionAdjuster.DEFAULT_GAP)
      }
      size.width = size.width.coerceAtMost(delta)
    }

    component.editors.forEach {
      it.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
        override fun recalculationEnds() {
          val height = (it as EditorImpl).offsetToXY(it.document.textLength).y + it.lineHeight + 6
          it.component.preferredSize = Dimension(it.component.preferredSize.width, min(height, MAX_HEIGHT))
          it.component.parent.invalidate()
          popup.pack(true, true)
        }

        override fun softWrapsChanged() {}
      })

      it.component.preferredSize = Dimension(max(size.width, MIN_WIDTH), min(it.component.preferredSize.height, MAX_HEIGHT))
    }

    popup.pack(true, true)
  }

  /**
   * Call when process is just activated via hotkey
   */
  fun activate() {
    justActivated = true
  }

  companion object {
    internal const val MAX_HEIGHT = 300
    internal const val MIN_WIDTH = 300

    fun getShortcutText(): String = KeymapUtil.getPreferredShortcutText(getShortcutSet().shortcuts)
    fun getShortcutSet(): ShortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_QUICK_JAVADOC)

    @TestOnly
    @JvmStatic
    fun getPreviewText(project: Project,
                       action: IntentionAction,
                       originalFile: PsiFile,
                       originalEditor: Editor): String? {
      return (getPreviewInfo(project, action, originalFile, originalEditor) as? IntentionPreviewDiffResult)?.psiFile?.text
    }

    /**
     * Returns content of preview:
     * if it's diff then new content is returned
     * if it's HTML then text representation is returned
     */
    @TestOnly
    @JvmStatic
    fun getPreviewContent(project: Project,
                          action: IntentionAction,
                          originalFile: PsiFile,
                          originalEditor: Editor): String {
      return when(val info = getPreviewInfo(project, action, originalFile, originalEditor)) {
        is IntentionPreviewDiffResult -> info.psiFile.text
        is Html -> info.content().toString()
        else -> ""
      }
    }

    @TestOnly
    @JvmStatic
    fun getPreviewInfo(project: Project,
                       action: IntentionAction,
                       originalFile: PsiFile,
                       originalEditor: Editor): IntentionPreviewInfo =
      ProgressManager.getInstance().runProcess<IntentionPreviewInfo>(
        { IntentionPreviewComputable(project, action, originalFile, originalEditor).generatePreview() },
        EmptyProgressIndicator()) ?: IntentionPreviewInfo.EMPTY
  }

  internal class IntentionPreviewPopupKey
}