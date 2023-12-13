// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.TextDiffType
import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.PathUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object TimelineDiffComponentFactory {

  fun createDiffComponentIn(cs: CoroutineScope,
                            project: Project,
                            editorFactory: EditorFactory,
                            patchHunk: PatchHunk,
                            anchor: DiffLineLocation,
                            anchorStart: DiffLineLocation?): JComponent {
    val truncatedHunk = truncateHunk(patchHunk, anchor, anchorStart)

    val anchorLineIndex = PatchHunkUtil.findHunkLineIndex(truncatedHunk, anchor)
    val anchorStartLineIndex = anchorStart?.takeIf { it != anchor }?.let { PatchHunkUtil.findHunkLineIndex(truncatedHunk, it) }
    val anchorRange = if (anchorLineIndex == null) {
      null
    }
    else if (anchorStartLineIndex != null) {
      LineRange(anchorStartLineIndex, anchorLineIndex + 1)
    }
    else {
      LineRange(anchorLineIndex, anchorLineIndex + 1)
    }

    return createDiffComponentIn(cs, project, editorFactory, truncatedHunk, anchorRange)
  }

  fun createDiffComponentIn(cs: CoroutineScope,
                            project: Project,
                            editorFactory: EditorFactory,
                            patchHunk: PatchHunk,
                            anchorLineRange: LineRange?): JComponent {
    if (patchHunk.lines.any { it.type != PatchLine.Type.CONTEXT }) {
      val appliedSplitHunks = GenericPatchApplier.SplitHunk.read(patchHunk).map {
        AppliedTextPatch.AppliedSplitPatchHunk(it, -1, -1, AppliedTextPatch.HunkStatus.NOT_APPLIED)
      }

      val state = PatchChangeBuilder().buildFromApplied(appliedSplitHunks)

      val patchContent = state.patchContent.removeSuffix("\n")

      return createDiffEditorIn(cs, project, editorFactory, patchContent).apply {
        gutter.setLineNumberConverter(LineNumberConverterAdapter(state.lineConvertor1.createConvertor()),
                                      LineNumberConverterAdapter(state.lineConvertor2.createConvertor()))


        state.hunks.forEach { hunk ->
          DiffDrawUtil.createUnifiedChunkHighlighters(this, hunk.patchDeletionRange, hunk.patchInsertionRange, null)
        }
        anchorLineRange?.let { highlightAnchor(it) }
      }.component
    }
    else {
      val patchContent = patchHunk.text.removeSuffix("\n")

      return createDiffEditorIn(cs, project, editorFactory, patchContent).apply {
        gutter.setLineNumberConverter(LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineBefore },
                                      LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineAfter })
        anchorLineRange?.let { highlightAnchor(it) }
      }.component
    }
  }

  private fun Editor.highlightAnchor(lineRange: LineRange) {
    DiffDrawUtil.createHighlighter(this, lineRange.start, lineRange.end, AnchorLine, false)
  }

  object AnchorLine : TextDiffType {
    override fun getName() = "Comment Anchor Line"

    override fun getColor(editor: Editor?): Color = JBColor.namedColor("Review.Timeline.Thread.Diff.AnchorLine",
                                                                       JBColor(0xFBF1D1, 0x544B2D))

    override fun getIgnoredColor(editor: Editor?) = getColor(editor)
    override fun getMarkerColor(editor: Editor?) = getColor(editor)
  }

  const val DIFF_CONTEXT_SIZE = 3

  private fun truncateHunk(hunk: PatchHunk, anchor: DiffLineLocation, anchorStart: DiffLineLocation?): PatchHunk {
    if (hunk.lines.size <= DIFF_CONTEXT_SIZE + 1) return hunk
    val actualAnchorStart = anchorStart?.takeIf { it != anchor } ?: anchor
    return truncateHunkAfter(truncateHunkBefore(hunk, actualAnchorStart), anchor)
  }

  private fun truncateHunkBefore(hunk: PatchHunk, location: DiffLineLocation): PatchHunk {
    val lines = hunk.lines
    if (lines.size <= DIFF_CONTEXT_SIZE + 1) return hunk
    val lineIdx = PatchHunkUtil.findHunkLineIndex(hunk, location) ?: return hunk
    val startIdx = lineIdx - DIFF_CONTEXT_SIZE
    return PatchHunkUtil.truncateHunkBefore(hunk, startIdx)
  }

  private fun truncateHunkAfter(hunk: PatchHunk, location: DiffLineLocation): PatchHunk {
    val lines = hunk.lines
    if (lines.size <= DIFF_CONTEXT_SIZE + 1) return hunk
    val lineIdx = PatchHunkUtil.findHunkLineIndex(hunk, location) ?: return hunk
    val endIdx = lineIdx + DIFF_CONTEXT_SIZE
    return PatchHunkUtil.truncateHunkAfter(hunk, endIdx)
  }

  fun createDiffEditorIn(cs: CoroutineScope, project: Project, editorFactory: EditorFactory, text: CharSequence): Editor {
    val document = editorFactory.createDocument(text)
    val editor = (editorFactory.createViewer(document, project, EditorKind.DIFF) as EditorEx).apply {
      gutterComponentEx.setPaintBackground(false)

      setRendererMode(true)
      setHorizontalScrollbarVisible(true)
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)
      isEmbeddedIntoDialogWrapper = true
      contentComponent.isOpaque = false

      setBorder(JBUI.Borders.empty())

      settings.apply {
        isShowIntentionBulb = false
        isCaretRowShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
        isRightMarginShown = false
        setRightMargin(-1)
        isFoldingOutlineShown = false
        isIndentGuidesShown = false
        isVirtualSpace = false
        isWheelFontChangeEnabled = false
        isAdditionalPageAtBottom = false
        lineCursorWidth = 1
      }
    }
    cs.awaitCancellationAndInvoke {
      editorFactory.releaseEditor(editor)
    }
    return editor
  }

  fun createDiffWithHeader(cs: CoroutineScope,
                           collapseVm: CollapsibleTimelineItemViewModel,
                           filePath: @NlsSafe String,
                           fileNameClickListener: Flow<ActionListener?>,
                           diffComponentFactory: CoroutineScope.() -> JComponent): JComponent {
    val expandCollapseButton = InlineIconButton(EmptyIcon.ICON_16).apply {
      cs.launch(start = CoroutineStart.UNDISPATCHED) {
        collapseVm.collapsed.collect { collapsed ->
          icon = if (collapsed) {
            AllIcons.General.ExpandComponent
          }
          else {
            AllIcons.General.CollapseComponent
          }
          hoveredIcon = if (collapsed) {
            AllIcons.General.ExpandComponentHover
          }
          else {
            AllIcons.General.CollapseComponentHover
          }

          actionListener = ActionListener {
            collapseVm.setCollapsed(!collapsed)
          }
        }
      }
      bindVisibilityIn(cs, collapseVm.collapsible)
    }



    return RoundedPanel(ListLayout.vertical(0), 8).apply {
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }

      add(cs.createFileNameComponent(filePath, expandCollapseButton, fileNameClickListener))
      bindChildIn(cs, collapseVm.collapsed.distinctUntilChanged()) { collapsed ->
        if (collapsed) return@bindChildIn null
        diffComponentFactory().apply {
          border = IdeBorderFactory.createBorder(SideBorder.TOP)
        }
      }
    }
  }

  fun createDiffWithHeader(cs: CoroutineScope,
                           filePath: @NlsSafe String,
                           fileNameClickListener: Flow<ActionListener?>,
                           diffComponent: JComponent): JComponent {
    return RoundedPanel(ListLayout.vertical(0), 8).apply {
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }

      add(cs.createFileNameComponent(filePath, null, fileNameClickListener))
      add(diffComponent.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      })
    }
  }

  private fun CoroutineScope.createFileNameComponent(filePath: String, expandCollapseButton: JComponent?,
                                                     nameClickListener: Flow<ActionListener?>): JComponent {
    val name = PathUtil.getFileName(filePath)
    val path = PathUtil.getParentPath(filePath)
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)

    val nameLabel = ActionLink(name).apply {
      icon = fileType.icon
      autoHideOnDisable = false
    }

    launch {
      nameClickListener.collect { listener ->
        nameLabel.actionListeners.forEach {
          nameLabel.removeActionListener(it)
        }
        if (listener != null) {
          nameLabel.addActionListener(listener)
        }
        nameLabel.isEnabled = listener != null
      }
    }

    return JPanel(MigLayout(LC().insets("0").gridGap("5", "0").fill().noGrid())).apply {
      isOpaque = false
      border = JBUI.Borders.empty(10)

      add(nameLabel)

      if (!path.isBlank()) add(JLabel(path).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().minWidth("0"))

      if (expandCollapseButton != null) {
        add(expandCollapseButton, CC().hideMode(3).gapLeft("10:push"))
      }
    }
  }
}