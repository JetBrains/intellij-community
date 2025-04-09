// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComponent.Companion.LOADING_PREVIEW
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewComponent.Companion.isNoPreviewPanel
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.Html
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.psi.PsiFile
import com.intellij.ui.ScreenUtil
import com.intellij.ui.popup.PopupPositionManager.Position.LEFT
import com.intellij.ui.popup.PopupPositionManager.Position.RIGHT
import com.intellij.ui.popup.PopupPositionManager.PositionAdjuster
import com.intellij.ui.popup.PopupUpdateProcessor
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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

@Service(Service.Level.PROJECT)
private class IntentionPreviewPopupUpdateProcessorCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)

class IntentionPreviewPopupUpdateProcessor internal constructor(
  private val project: Project,
  private val fn: (Any?) -> IntentionPreviewInfo,
) : PopupUpdateProcessor(project) {
  private var index: Int = LOADING_PREVIEW
  private var show = false
  private var originalPopup: IntentionPreviewComponentHolder? = null
  private val editorsToRelease = mutableListOf<EditorEx>()
  private var job: Job? = null

  private lateinit var popup: JBPopup
  private lateinit var component: IntentionPreviewComponent
  private var justActivated: Boolean = false

  private fun getPopupWindow(): JWindow? = UIUtil.getParentOfType(JWindow::class.java, popup.content)

  override fun updatePopup(intentionAction: Any?) {
    if (!show) {
      return
    }

    if (!::popup.isInitialized || popup.isDisposed) {
      val origPopup = originalPopup?.takeIf { !it.isDisposed() } ?: return

      component = IntentionPreviewComponent(origPopup)

      component.multiPanel.select(LOADING_PREVIEW, true)

      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
        .setCancelCallback { cancel() }
        .setCancelKeyEnabled(false)
        .addUserData(IntentionPreviewPopupKey())
        .createPopup()

      component.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          val size = popup.size
          val insets = popup.content.insets
          popup.size = Dimension((size.width - insets.width).coerceAtLeast(MIN_WIDTH), size.height - insets.height)
          adjustPosition(originalPopup, true)
        }
      })
      adjustPosition(originalPopup)
      addMoveListener(originalPopup) { adjustPosition(originalPopup) }
    }

    val oldJob = job
    oldJob?.cancel()

    component.multiPanel.getValue(index, false)?.let {
      select(index)
      return
    }

    component.startLoading()

    val modality = ModalityState.defaultModalityState().asContextElement()
    job = project.service<IntentionPreviewPopupUpdateProcessorCoroutineScopeHolder>().coroutineScope.launch {
      oldJob?.join()

      val info = readAction {
        postprocess(fn(intentionAction))
      }
      withContext(Dispatchers.EDT + modality) {
        select(index, renderPreview(info))
      }
    }.also {
      it.cancelOnDispose(popup)
    }
  }

  private fun addMoveListener(popup: IntentionPreviewComponentHolder?, action: () -> Unit) {
    if (popup == null) {
      return
    }

    popup.jComponent().addHierarchyBoundsListener(object : HierarchyBoundsAdapter() {
      override fun ancestorMoved(e: HierarchyEvent?) {
        action.invoke()
      }
    })
  }

  private fun adjustPosition(originalPopup: IntentionPreviewComponentHolder?, checkResizing: Boolean = false) {
    if (popup.isDisposed || originalPopup == null || !originalPopup.jComponent().isShowing) {
      return
    }

    val positionAdjuster = PositionAdjuster(originalPopup.jComponent())
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
          IntentionPreviewComponent.createNoPreviewPanel()
        }
        else {
          val location = popup.locationOnScreen
          val screen = ScreenUtil.getScreenRectangle(location)

          var delta = screen.width + screen.x - location.x
          val content = originalPopup?.jComponent()
          val origLocation = if (content?.isShowing == true) content.locationOnScreen else null
          // On the left side of the original popup: avoid overlap
          if (origLocation != null && location.x < origLocation.x) {
            delta = delta.coerceAtMost(origLocation.x - screen.x - PositionAdjuster.DEFAULT_GAP)
          }

          for (editor in editors) {
            editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
              override fun recalculationEnds() {
                val height = (editor as EditorImpl).offsetToXY(editor.document.textLength).y + editor.lineHeight + 6
                editor.component.preferredSize = Dimension(
                  max(editor.component.preferredSize.width, MIN_WIDTH).coerceAtMost(delta),
                  min(height, MAX_HEIGHT)
                )
                editor.component.parent?.invalidate()
                popup.pack(true, true)
              }

              override fun softWrapsChanged() {}
            })

            editor.component.preferredSize = Dimension(
              max(editor.component.preferredSize.width, MIN_WIDTH).coerceAtMost(delta),
              min(editor.component.preferredSize.height, MAX_HEIGHT)
            )
          }

          editorsToRelease.addAll(editors)
          IntentionPreviewEditorsPanel(editors.toMutableList())
        }
      }
      is Html -> IntentionPreviewComponent.createHtmlPanel(result)
      else -> IntentionPreviewComponent.createNoPreviewPanel()
    }
  }

  fun setup(popup: IntentionPreviewComponentHolder, parentIndex: Int) {
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
    job?.cancel()

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
    getPopupWindow()?.isVisible = !selectedComponent.isNoPreviewPanel() || justActivated
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
     * if it's a diff then new content is returned
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

    @Suppress("UsagesOfObsoleteApi")
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

/**
 * ComponentHolder is used to get the component of the popup.
 * It's needed to get the size of the popup and position it correctly.
 *
 * The component can be obtained after the popup is shown by calling [IntentionPreviewComponentHolder.jComponent].
 *
 * The popup can be disposed by calling [ComponentHolder.dispose].
 *
 */
@ApiStatus.Experimental
interface IntentionPreviewComponentHolder : Disposable {
  fun jComponent(): JComponent
  fun isDisposed(): Boolean
}
