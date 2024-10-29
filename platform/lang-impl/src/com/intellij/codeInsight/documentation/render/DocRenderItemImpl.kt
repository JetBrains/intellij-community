// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.CustomFoldRegionRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.ui.LayeredIcon
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent


private val IS_DOC_RENDER_ITEM_IMPL_FOLDING = Key.create<Boolean>("is.doc.render.item.impl.folding")
/**
 * Is used to indicate whether the custom folding region was produced by a [DocRenderItemImpl] instance or not.
 *
 * It is useful in Rider, when trying to merge the render documentation foldings from the backend into the frontend folding model.
 */
var CustomFoldRegion.isDocRenderImplFolding
  get() = this.getUserData(IS_DOC_RENDER_ITEM_IMPL_FOLDING)
  set(value) = this.putUserData(IS_DOC_RENDER_ITEM_IMPL_FOLDING, value)

internal class DocRenderItemImpl(override val editor: Editor,
                                 textRange: TextRange,
                                 override var textToRender: @Nls String?,
                                 private val docRendererFactory: (DocRenderItem) -> DocRenderer,
                                 private val inlineDocumentationFinder: InlineDocumentationFinder?,
                                 val isZombie: Boolean = false) : DocRenderItem, MutableDocRenderItem {
  override val highlighter: RangeHighlighter
  override var foldRegion: CustomFoldRegion? = null
    private set

  override fun calcFoldingGutterIconRenderer(): GutterIconRenderer? {
    val isHighlighterIconVisible = (highlighter.gutterIconRenderer as? MyGutterIconRenderer)?.isIconVisible ?: return null
    return MyGutterIconRenderer(AllIcons.Gutter.JavadocEdit, isHighlighterIconVisible)
  }

  init {
    highlighter = (editor.markupModel as MarkupModelEx)
      .addRangeHighlighterAndChangeAttributes(null, textRange.startOffset, textRange.endOffset,
                                              0, HighlighterTargetArea.EXACT_RANGE, false) { h: RangeHighlighterEx ->
        h.putUserData(DocRenderItemManagerImpl.OWNS_HIGHLIGHTER, true)
      }
    updateIcon(null)
  }

  val isValid: Boolean
    get() = highlighter.isValid && highlighter.startOffset < highlighter.endOffset && ItemLocation(highlighter).matches(foldRegion)

  fun remove(foldingTasks: MutableCollection<Runnable>): Boolean {
    highlighter.dispose()
    val region = foldRegion
    if (region != null && region.isValid) {
      foldingTasks.add(Runnable { region.editor.foldingModel.removeFoldRegion(region) })
      return true
    }
    return false
  }

  override fun toggle() {
    if (!isValid) return
    toggle(null)
  }

  fun toggle(foldingTasks: MutableCollection<Runnable>?): Boolean {
    if (editor !is EditorEx) return false
    val foldingModel = editor.foldingModel
    val region = foldRegion
    if (region == null) {
      if (textToRender == null && foldingTasks == null) {
        generateHtmlInBackgroundAndToggle()
        return false
      }
      val offsets = ItemLocation(highlighter)
      val foldingTask = Runnable {
        foldRegion = foldingModel.addCustomLinesFolding(offsets.foldStartLine, offsets.foldEndLine, docRendererFactory(this))
        foldRegion?.isDocRenderImplFolding = true
      }
      foldingTasks?.add(foldingTask) ?: foldingModel.runBatchFoldingOperation(foldingTask, true, false)
    }
    else {
      val foldingTask = Runnable {
        val startOffset = region.startOffset
        val endOffset = region.endOffset
        foldingModel.removeFoldRegion(region)
        for (r in foldingModel.getRegionsOverlappingWith(startOffset, endOffset)) {
          if (r.startOffset >= startOffset && r.endOffset <= endOffset) {
            r.isExpanded = true
          }
        }
        foldRegion = null
      }
      foldingTasks?.add(foldingTask) ?: foldingModel.runBatchFoldingOperation(foldingTask, true, false)
      if (!DocRenderManager.isDocRenderingEnabled(editor)) {
        // the value won't be updated by DocRenderPass on document modification, so we shouldn't cache the value
        textToRender = null
      }
    }
    return true
  }

  private fun generateHtmlInBackgroundAndToggle() {
    ReadAction.nonBlocking<String> { DocRenderPassFactory.calcText(getInlineDocumentation()) }
      .withDocumentsCommitted(editor.project ?: return)
      .coalesceBy(this)
      .finishOnUiThread(ModalityState.any()) { html: @Nls String? ->
        textToRender = html
        toggle()
      }.submit(AppExecutorUtil.getAppExecutorService())
  }

  override fun getInlineDocumentation() = inlineDocumentationFinder?.getInlineDocumentation(this)

  override fun getInlineDocumentationTarget() = inlineDocumentationFinder?.getInlineDocumentationTarget(this)

  fun updateIcon(foldingTasks: List<Runnable>?) {
    val iconEnabled = DocRenderDummyLineMarkerProvider.isGutterIconEnabled()
    val iconExists = highlighter.gutterIconRenderer != null
    if (iconEnabled != iconExists) {
      highlighter.gutterIconRenderer = if (iconEnabled) MyGutterIconRenderer(AllIcons.Gutter.JavadocRead, false) else null
      (foldRegion?.renderer as? DocRenderer)?.update(false, false, foldingTasks)
    }
  }

  override fun setIconVisible(visible: Boolean) {
    val iconRenderer = highlighter.gutterIconRenderer as MyGutterIconRenderer?
    if (iconRenderer != null) {
      iconRenderer.isIconVisible = visible
      val y = editor.visualLineToY((editor as EditorImpl).offsetToVisualLine(highlighter.startOffset))
      repaintGutter(y)
    }
    val region = foldRegion
    if (region != null) {
      val inlayIconRenderer = region.gutterIconRenderer as MyGutterIconRenderer?
      if (inlayIconRenderer != null) {
        inlayIconRenderer.isIconVisible = visible
        repaintGutter(editor.offsetToXY(region.startOffset).y)
      }
    }
  }

  private fun repaintGutter(startY: Int) {
    val gutter = editor.gutter as JComponent
    gutter.repaint(0, startY, gutter.width, startY + editor.lineHeight)
  }

  @JvmInline
  private value class ItemLocation(private val highlighter: RangeHighlighter) {
    val foldStartLine
      get() = highlighter.document.getLineNumber(highlighter.startOffset)
    val foldEndLine
      get() = highlighter.document.getLineNumber(highlighter.endOffset)

    fun matches(foldRegion: CustomFoldRegion?): Boolean {
      return foldRegion == null ||
             foldRegion.isValid &&
             foldRegion.startOffset == foldRegion.editor.document.getLineStartOffset(foldStartLine) &&
             foldRegion.endOffset == foldRegion.editor.document.getLineEndOffset(foldEndLine)
    }
  }

  private inner class MyGutterIconRenderer(icon: Icon, iconVisible: Boolean) : GutterIconRenderer(), DumbAware {
    private val icon = LayeredIcon.layeredIcon(arrayOf(icon))

    var isIconVisible: Boolean
      get() = icon.isLayerEnabled(0)
      set(visible) {
        icon.setLayerEnabled(0, visible)
      }

    init {
      isIconVisible = iconVisible
    }

    override fun equals(other: Any?): Boolean {
      return other is MyGutterIconRenderer
    }

    override fun hashCode(): Int {
      return 0
    }

    override fun getIcon(): Icon {
      return icon
    }

    override fun getAccessibleName(): String {
      return CodeInsightBundle.message("doc.render.icon.accessible.name")
    }

    override fun getAlignment(): Alignment {
      return Alignment.RIGHT
    }

    override fun isNavigateAction(): Boolean {
      return true
    }

    override fun getTooltipText(): String? {
      val action = ActionManager.getInstance().getAction(IdeActions.ACTION_TOGGLE_RENDERED_DOC) ?: return null
      val actionText = action.templateText ?: return null
      return XmlStringUtil.wrapInHtml(actionText + HelpTooltip.getShortcutAsHtml(KeymapUtil.getFirstKeyboardShortcutText(action)))
    }

    override fun getClickAction(): AnAction {
      return DocRenderer.ToggleRenderingAction(this@DocRenderItemImpl)
    }

    override fun getPopupMenuActions(): ActionGroup? {
      return ActionManager.getInstance().getAction(IdeActions.GROUP_DOC_COMMENT_GUTTER_ICON_CONTEXT_MENU) as? ActionGroup
    }
  }

  companion object {
    @JvmStatic
    fun createDemoRenderer(editor: Editor): CustomFoldRegionRenderer {
      val item = DocRenderItemImpl(editor, TextRange(0, 0), CodeInsightBundle.message(
        "documentation.rendered.documentation.with.href.link"), { DocRenderer(it) },
                                   InlineDocumentationFinder.getInstance(editor.project ?: ProjectManager.getInstance().defaultProject))
      return DocRenderer(item, DocRenderDefaultLinkActivationHandler)
    }
  }
}
