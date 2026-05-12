// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.intentions

import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.tools.fragmented.SimpleUnifiedFragmentBuilder
import com.intellij.diff.tools.fragmented.UnifiedDiffHighlightersData
import com.intellij.diff.tools.fragmented.UnifiedDiffPanel
import com.intellij.diff.tools.fragmented.UnifiedEditorHighlighter
import com.intellij.diff.tools.fragmented.UnifiedEditorRangeHighlighter
import com.intellij.diff.tools.fragmented.UnifiedFoldingModel
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffLineNumberConverter
import com.intellij.diff.util.DiffPlaces
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import javax.swing.Icon

@ApiStatus.Internal
object IntentionDiffFeatureKeys {
  @JvmField val IS_UNIFIED_DIFF_INTENTION_PREVIEW: Key<Boolean?> = Key.create("Diff.UnifiedDiffIntentionPreview")
}

@ApiStatus.Internal
data class CreatedPreviewUI(
  val unifiedDocument: Document, val unifiedEditor: EditorEx, val foldingModel: UnifiedFoldingModel, val panel: UnifiedDiffPanel,
  @field:NlsSafe val fileNameForDisplay: String, val fileTypeIcon: Icon, val isFileNameTrivial: Boolean,
)

@ApiStatus.Internal
object IntentionDiffUtil {

  @RequiresEdt
  fun createPreviewUi(
    project: Project, diffDisposable: Disposable, fileNameDisplay: String, fileTypeIcon: Icon, contextName: String? = null,
  ): CreatedPreviewUI {
    val unifiedDocument = EditorFactory.getInstance().createDocument("")
    val unifiedEditor = DiffUtil.createEditor(
      unifiedDocument, project, true, true,
      Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR),
      /*additionalEditorLinesAfterEnd =*/0
    ).apply {
      putUserData(IntentionDiffFeatureKeys.IS_UNIFIED_DIFF_INTENTION_PREVIEW, true)
    }
    val unifiedFoldingModel = UnifiedFoldingModel(project, unifiedEditor, diffDisposable)
    val unifiedPanel = UnifiedDiffPanel(project, unifiedEditor.component)

    return CreatedPreviewUI(unifiedDocument, unifiedEditor, unifiedFoldingModel, unifiedPanel, fileNameDisplay, fileTypeIcon, contextName != null && contextName == fileNameDisplay)
  }

  @RequiresBackgroundThread
  suspend fun applyDiffToUi(
    project: Project, diffDisposable: Disposable, content1: DocumentContent, content2: DocumentContent, ui: CreatedPreviewUI,
  ) {
    val (unifiedDocument, unifiedEditor, unifiedFoldingModel, unifiedPanel) = ui

    val masterSide = Side.RIGHT

    val foldingSettings = TextDiffViewerUtil.getFoldingModelSettings(TextDiffSettingsHolder.TextDiffSettings.getSettings(DiffPlaces.INTENTION_PREVIEW))
    val textSettings = TextDiffSettingsHolder.TextDiffSettings.getSettings(DiffPlaces.INTENTION_PREVIEW)

    val diffProvider = DiffUtil.createDefaultDiffComputerNoIgnoreDiffProvider(
      project, content1, content2, textSettings, Runnable { }, diffDisposable
    )

    val document1 = content1.document
    val document2 = content2.document

    val (text1, text2) = readAction { document1.immutableCharSequence to document2.immutableCharSequence }

    val fragments = coroutineToIndicator { indicator ->
      diffProvider.compare(text1, text2, indicator)
    }

    val builder = SimpleUnifiedFragmentBuilder(document1, document2, masterSide).exec(fragments)

    val unifiedHighlightersData = readAction {
      val highlighter = UnifiedEditorHighlighter.buildHighlighter(
        project, unifiedDocument, content1, content2, text1, text2, builder.ranges, builder.text.length
      )
      val rangeHighlighter = UnifiedEditorRangeHighlighter(project, document1, document2, builder.ranges)
      UnifiedDiffHighlightersData(highlighter, rangeHighlighter)
    }

    val convertor1 = builder.convertor1
    val convertor2 = builder.convertor2
    val changedLines = builder.changedLines

    val foldingState = unifiedFoldingModel.createState(
      changedLines, foldingSettings, masterSide.select(document1, document2),
      masterSide.select(convertor1, convertor2), StringUtil.countNewLines(builder.text) + 1,
      /*materialiseEmptyRegions = */true
    )

    writeIntentReadAction {
      val foldingLinePredicate = unifiedFoldingModel.hideLineNumberPredicate(0)
      val mergedLineConverter = DiffUtil.mergeLineConverters(
        DiffUtil.getContentLineConvertor(content1), convertor1.createConvertor())

      unifiedEditor.getGutter().setLineNumberConverter(
        DiffLineNumberConverter(foldingLinePredicate, mergedLineConverter),
        null
      )

      runWriteAction { unifiedDocument.setText(builder.text) }

      DiffUtil.setEditorCodeStyle(project, unifiedEditor, masterSide.select(content1, content2))

      unifiedFoldingModel.install(foldingState, null, foldingSettings, false, true)

      for (change in builder.changes) {
        DiffDrawUtil.createUnifiedChunkHighlighters(
          unifiedEditor, change.deletedRange, change.insertedRange,
          change.isExcluded, change.isSkipped, change.lineFragment.innerFragments
        )
      }

      UnifiedDiffHighlightersData.apply(project, unifiedEditor, unifiedHighlightersData)

      unifiedPanel.setGoodContent()
      unifiedEditor.getGutterComponentEx().revalidateMarkup()
    }
  }
}