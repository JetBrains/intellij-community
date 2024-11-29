// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Document
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.DocumentUtil
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.ApiStatus
import java.util.function.IntFunction

class DeclarativeInlayHintsPass(
  private val rootElement: PsiElement,
  private val editor: Editor,
  private val providerInfos: List<InlayProviderPassInfo>,
  private val isPreview: Boolean,
  private val isProviderDisabled: Boolean = false
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true), DumbAware {
  private var preprocessedInlayData: PreprocessedInlayData = PreprocessedInlayData.EMPTY

  override fun doCollectInformation(progress: ProgressIndicator) {
    val ownCollectors = ArrayList<CollectionInfo<OwnBypassCollector>>()
    val sharedCollectors = ArrayList<CollectionInfo<SharedBypassCollector>>()
    val sinks = ArrayList<InlayTreeSinkImpl>()
    for (providerInfo in providerInfos) {
      val provider = providerInfo.provider
      if (DumbService.isDumb(myProject) && !DumbService.isDumbAware(provider)) {
        continue
      }

      val sink = InlayTreeSinkImpl(providerInfo.providerId, providerInfo.optionToEnabled, isPreview, isProviderDisabled, provider.javaClass, passSourceId)
      sinks.add(sink)
      when (val collector = createCollector(provider)) {
        is OwnBypassCollector -> ownCollectors.add(CollectionInfo(sink, collector))
        is SharedBypassCollector -> sharedCollectors.add(CollectionInfo(sink, collector))
        null -> {}
      }
    }
    for ((sink, collector) in ownCollectors) {
      collector.collectHintsForFile(myFile, sink)
    }
    val traverser = SyntaxTraverser.psiTraverser(rootElement)
    for (element in traverser) {
      for ((sink, collector) in sharedCollectors) {
        collector.collectFromElement(element, sink)
      }
    }

    preprocessedInlayData = preprocessCollectedInlayData(sinks.flatMap { it.finish() }, document)
  }

  private data class CollectionInfo<T>(
    val sink: InlayTreeSinkImpl,
    val collector: T
  )

  internal data class AboveLineIndentedPositionDetail(val line: Int, val inlayData: InlayData) {
    val aboveLineIndentedPosition: AboveLineIndentedPosition get() =
      inlayData.position as? AboveLineIndentedPosition
      ?: throw IllegalStateException("Expected AboveLineIndentedPosition, got ${inlayData.position}")
  }

  @ApiStatus.Internal
  class PreprocessedInlayData internal constructor(
    internal val inline: List<InlayData>,
    internal val endOfLine: List<InlayData>,
    internal val aboveLine: List<AboveLineIndentedPositionDetail>,
  ) {
    companion object {
      val EMPTY = PreprocessedInlayData(emptyList(), emptyList(), emptyList())
    }
  }

  override fun doApplyInformationToEditor() {
    val positionKeeper = EditorScrollingPositionKeeper(editor)
    positionKeeper.savePosition()
    applyInlayData(editor, myFile.project, preprocessedInlayData, passSourceId)
    positionKeeper.restorePosition(false)
  }

  companion object {
    @ApiStatus.Internal
    val passSourceId: String = DeclarativeInlayHintsPass::class.java.name

    @RequiresEdt
    @ApiStatus.Internal
    fun applyInlayData(editor: Editor, project: Project, preprocessedInlayData: PreprocessedInlayData, sourceId: String) {
      val inlayModel = editor.inlayModel
      val document = editor.document
      val offsetToExistingInlineInlays = groupRelevantExistingInlays(
        sourceId,
        inlayModel.getInlineElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java),
        groupKey = { inlay -> inlay.offset }
      )
      val offsetToExistingEolInlays = groupRelevantExistingInlays(
        sourceId,
        inlayModel.getAfterLineEndElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java),
        groupKey = { inlay -> inlay.offset }
      )
      val offsetToExistingBlockInlays = groupRelevantExistingInlays(
        sourceId,
        inlayModel.getBlockElementsInRange(0, document.textLength, DeclarativeIndentedBlockInlayRenderer::class.java),
        groupKey = { inlay -> document.getLineNumber(inlay.offset) }
      )
      val storage = InlayHintsUtils.getTextMetricStorage(editor)

      fun ensureConsistentSourceId(inlayData: InlayData) {
        if (inlayData.sourceId != sourceId) {
          throw IllegalStateException("Inconsistent sourceId=$sourceId, inlayData=$inlayData")
        }
      }
      for (inlayData in preprocessedInlayData.endOfLine) {
        ensureConsistentSourceId(inlayData)
        val position = inlayData.position as EndOfLinePosition
        val lineEndOffset = editor.document.getLineEndOffset(position.line)
        val updated = tryUpdateInlayAndRemoveFromDeleteList(
          offsetToExistingEolInlays, inlayData, lineEndOffset,
          require = { inlay -> inlay.renderer.providerId == inlayData.providerId }
        )
        if (!updated) {
          val renderer = DeclarativeInlayRenderer(inlayData, storage, inlayData.providerId, sourceId)
          val properties = InlayProperties()
            .priority(position.priority)
            .relatesToPrecedingText(true)
            .disableSoftWrapping(false)
          val inlay = inlayModel.addAfterLineEndElement(lineEndOffset, properties, renderer)
          if (inlay != null) {
            renderer.initInlay(inlay)
          }
        }
      }
      for (inlayData in preprocessedInlayData.inline) {
        ensureConsistentSourceId(inlayData)
        val position = inlayData.position as InlineInlayPosition
        val updated = tryUpdateInlayAndRemoveFromDeleteList(
          offsetToExistingInlineInlays, inlayData, position.offset,
          require = { inlay -> inlay.renderer.providerId == inlayData.providerId }
        )
        if (!updated) {
          val renderer = DeclarativeInlayRenderer(inlayData, storage, inlayData.providerId, sourceId)
          val inlay = inlayModel.addInlineElement(position.offset, position.relatedToPrevious, position.priority, renderer)
          if (inlay != null) {
            renderer.initInlay(inlay)
          }
        }
      }
      preprocessedInlayData.aboveLine.forEachRun { line, providerId, verticalPriority, inlayData ->
        inlayData.forEach { ensureConsistentSourceId(it) }
        val updated = tryUpdateInlayAndRemoveFromDeleteList(
          offsetToExistingBlockInlays, inlayData, line,
          require = { inlay -> inlay.renderer.providerId == providerId && inlay.properties.priority == verticalPriority }
        )
        if (!updated) {
          val renderer = DeclarativeIndentedBlockInlayRenderer(
            inlayData, storage, providerId, sourceId, DocumentUtil.getLineStartIndentedOffset(document, line)
          )
          val inlay = inlayModel.addBlockElement(
            inlayData.minOf { (it.position as AboveLineIndentedPosition).offset }, false, true, verticalPriority, renderer
          )
          if (inlay != null) {
            renderer.initInlay(inlay)
          }
        }
      }

      deleteNotPreservedInlays(offsetToExistingInlineInlays)
      deleteNotPreservedInlays(offsetToExistingEolInlays)
      deleteNotPreservedInlays(offsetToExistingBlockInlays)

      DeclarativeInlayHintsPassFactory.updateModificationStamp(editor, project)
    }

    private fun deleteNotPreservedInlays(offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<*>>>) {
      for (inlays in offsetToExistingInlays.values) {
        for (inlay in inlays) {
          Disposer.dispose(inlay)
        }
      }
    }

    private fun <M> tryUpdateInlayAndRemoveFromDeleteList(
      offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<out DeclarativeInlayRendererBase<M>>>>,
      inlayData: M,
      groupKey: Int,
      require: (Inlay<out DeclarativeInlayRendererBase<M>>) -> Boolean
    ): Boolean {
      val inlays = offsetToExistingInlays.get(groupKey)
      if (inlays == null) return false
      val iterator = inlays.iterator()
      while (iterator.hasNext()) {
        val existingInlay = iterator.next()
        if (require(existingInlay)) {
          existingInlay.renderer.updateModel(inlayData)
          existingInlay.update()
          iterator.remove()
          return true
        }
      }
      return false
    }

    /**
     * [document] must be unchanged from when [inlayData] were collected
     */
    @ApiStatus.Internal
    @RequiresReadLock
    fun preprocessCollectedInlayData(inlayData: List<InlayData>, document: Document): PreprocessedInlayData {
      val inlineData = mutableListOf<InlayData>()
      val eolData = mutableListOf<InlayData>()
      val aboveLineData = mutableListOf<AboveLineIndentedPositionDetail>()

      for (inlayData in inlayData) {
        when (val position = inlayData.position) {
          is AboveLineIndentedPosition -> {
            val line = document.getLineNumber(position.offset)
            aboveLineData.add(AboveLineIndentedPositionDetail(line, inlayData))
          }
          is EndOfLinePosition -> eolData.add(inlayData)
          is InlineInlayPosition -> inlineData.add(inlayData)
        }
      }

      // The sort must be stable, so that insertion order from providers is maintained. See the AboveLineIndentedPosition doc.
      aboveLineData.sortWith(compareBy<AboveLineIndentedPositionDetail> { it.line }
                               .thenBy { it.inlayData.providerId }
                               .thenBy { it.aboveLineIndentedPosition.verticalPriority }
                               .thenByDescending { it.aboveLineIndentedPosition.priority })

      return PreprocessedInlayData(
        inlineData,
        eolData,
        aboveLineData
      )
    }

    private inline fun List<AboveLineIndentedPositionDetail>.forEachRun(action: (Int, String, Int, List<InlayData>) -> Unit) {
      if (isEmpty()) return
      // run ⇔ group of inlays with the same line, provider and vertical priority
      // preprocessedInlayData.aboveLine are sorted by these properties
      val initial = first()
      var line = initial.line
      var providerId = initial.inlayData.providerId
      var verticalPriority = initial.aboveLineIndentedPosition.verticalPriority
      var runStart = 0 // inclusive
      var runEnd = 1 // exclusive

      val iter = this.withIndex().iterator()
      iter.next()
      while (iter.hasNext()) {
        val (index, item) = iter.next()
        if (item.line != line ||
            item.inlayData.providerId != providerId ||
            item.aboveLineIndentedPosition.verticalPriority != verticalPriority) {
          // end of a run, apply it
          action(line, providerId, verticalPriority,
                this.subList(runStart, index).map { it.inlayData })
          // set up a new run
          line = item.line
          providerId = item.inlayData.providerId
          verticalPriority = item.aboveLineIndentedPosition.verticalPriority
          runStart = index
        }
        runEnd = index + 1
      }
      if (runStart < runEnd) {
        action(line, providerId, verticalPriority,
               this.subList(runStart, runEnd).map { it.inlayData })
      }
    }
  }

  private fun createCollector(provider: InlayHintsProvider): InlayHintsCollector? {
    return provider.createCollector(myFile, editor)
  }
}

private fun <Rend : DeclarativeInlayRendererBase<*>> groupRelevantExistingInlays(
  sourceId: String,
  inlays: List<Inlay<out Rend>>,
  groupKey: (Inlay<*>) -> Int
): Int2ObjectOpenHashMap<SmartList<Inlay<out Rend>>> {
  val grouped = Int2ObjectOpenHashMap<SmartList<Inlay<out Rend>>>()
  for (inlay in inlays) {
    if (inlay.renderer.sourceId != sourceId) continue
    val key = groupKey(inlay)
    grouped.computeIfAbsent(key, IntFunction { SmartList() }).add(inlay)
  }
  return grouped
}