// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Document
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer
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
  private var ignoredProviderIds: Set<String> = emptySet()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val ownCollectors = ArrayList<CollectionInfo<OwnBypassCollector>>()
    val sharedCollectors = ArrayList<CollectionInfo<SharedBypassCollector>>()
    val sinks = ArrayList<InlayTreeSinkImpl>()
    val ignoredProviderIds = mutableSetOf<String>()
    for (providerInfo in providerInfos) {
      val provider = providerInfo.provider
      if (DumbService.isDumb(myProject) && !DumbService.isDumbAware(provider)) {
        ignoredProviderIds.add(providerInfo.providerId)
        continue
      }

      val sink = InlayTreeSinkImpl(providerInfo.providerId, providerInfo.optionToEnabled, isPreview, isProviderDisabled,
                                   provider.javaClass, passSourceId)
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
    this.ignoredProviderIds = ignoredProviderIds
  }

  private data class CollectionInfo<T>(
    val sink: InlayTreeSinkImpl,
    val collector: T
  )

  @ApiStatus.Internal
  class PreprocessedInlayData internal constructor(
    internal val inline: List<Plan.Inline>,
    internal val eol: List<Plan.Eol>,
    internal val aboveLine: List<Plan.AboveLine>,
  ) {
    companion object {
      val EMPTY: PreprocessedInlayData = PreprocessedInlayData(emptyList(), emptyList(), emptyList())
    }

    // information about a single inlay add/update operation to be executed
    internal abstract class Plan<T>(val inlayData: T) {
      // null -> add; non-null -> update
      var updateTarget: Inlay<out DeclarativeInlayRendererBase<T>>? = null

      class Inline(data: InlayData) : Plan<InlayData>(data)
      class Eol(data: InlayData, val lineEndOffset: Int) : Plan<InlayData>(data)
      class AboveLine(
        data: List<InlayData>,
        val line: Int,
        val initialIndentOffset: Int,
        val smallestOffset: Int,
      ) : Plan<List<InlayData>>(data)
    }
  }

  override fun doApplyInformationToEditor() {
    val positionKeeper = EditorScrollingPositionKeeper(editor)
    positionKeeper.savePosition()
    applyInlayData(editor, myFile.project, preprocessedInlayData, ignoredProviderIds, passSourceId)
    positionKeeper.restorePosition(false)
  }

  companion object {
    @ApiStatus.Internal
    val passSourceId: String = DeclarativeInlayHintsPass::class.java.name

    @RequiresEdt
    @ApiStatus.Internal
    fun applyInlayData(
      editor: Editor,
      project: Project,
      preprocessedInlayData: PreprocessedInlayData,
      ignoredProviderIds: Set<String>,
      sourceId: String,
    ) {
      val inlayModel = editor.inlayModel
      val document = editor.document
      val offsetToExistingInlineInlays = groupRelevantExistingInlays(
        filter = { it.renderer.sourceId == sourceId && it.renderer.providerId !in ignoredProviderIds },
        inlayModel.getInlineElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java),
        groupKey = { inlay -> inlay.offset }
      )
      val offsetToExistingEolInlays = groupRelevantExistingInlays(
        filter = { it.renderer.sourceId == sourceId && it.renderer.providerId !in ignoredProviderIds },
        inlayModel.getAfterLineEndElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java),
        groupKey = { inlay -> inlay.offset }
      )
      val offsetToExistingBlockInlays = groupRelevantExistingInlays(
        filter = { it.renderer.sourceId == sourceId && it.renderer.providerId !in ignoredProviderIds },
        inlayModel.getBlockElementsInRange(0, document.textLength, DeclarativeIndentedBlockInlayRenderer::class.java),
        groupKey = { inlay -> document.getLineNumber(inlay.offset) }
      )

      fun checkConsistentSourceId(inlayData: InlayData) =
        check(inlayData.sourceId == sourceId) { "Inconsistent sourceId=$sourceId, inlayData=$inlayData" }

      for (plan in preprocessedInlayData.eol) {
        checkConsistentSourceId(plan.inlayData)
        plan.updateTarget = findSuitableInlayAndRemoveFromDeleteList(
          offsetToExistingEolInlays,
          groupKey = plan.lineEndOffset,
          require = { inlay -> inlay.renderer.providerId == plan.inlayData.providerId }
        )
      }
      for (plan in preprocessedInlayData.inline) {
        checkConsistentSourceId(plan.inlayData)
        plan.inlayData.position as InlineInlayPosition
        plan.updateTarget = findSuitableInlayAndRemoveFromDeleteList(
          offsetToExistingInlineInlays,
          groupKey = plan.inlayData.position.offset,
          require = { inlay ->
            inlay.renderer.providerId == plan.inlayData.providerId &&
            inlay.isRelatedToPrecedingText == plan.inlayData.position.relatedToPrevious
          }
        )
      }
      for (plan in preprocessedInlayData.aboveLine) {
        plan.inlayData.forEach { checkConsistentSourceId(it) }
        plan.updateTarget = findSuitableInlayAndRemoveFromDeleteList(
          offsetToExistingBlockInlays,
          groupKey = plan.line,
          require = { inlay ->
            // providerId, verticalPriority must be equal in a single Plan.AboveLine (see #forEachRun)
            inlay.renderer.providerId == plan.inlayData.first().providerId &&
            inlay.properties.priority == plan.inlayData.first().aboveLinePosition().verticalPriority
          }
        )
      }

      val isBulk = run {
        // Inlay updates do not result in InlayModel.Listener events in most cases
        // (e.g., the text does not change, so the size does not change).
        val adds = with(preprocessedInlayData) {
          inline.count { it.updateTarget == null } +
          eol.count { it.updateTarget == null } +
          aboveLine.count { it.updateTarget == null }
        }
        val removals = offsetToExistingEolInlays.size +
                       offsetToExistingInlineInlays.size +
                       offsetToExistingBlockInlays.size
        adds + removals > INLAY_BATCH_MODE_THRESHOLD
      }

      val textMetricsStorage = InlayHintsUtils.getTextMetricStorage(editor)
      val publisher = project.messageBus.syncPublisher(DeclarativeInlayUpdateListener.TOPIC)

      editor.inlayModel.execute(isBulk) {
        for (plan in preprocessedInlayData.eol) {
          val inlayData = plan.inlayData
          val target = plan.updateTarget
          if (target != null) {
            val oldInlayData = updateInlay(target, inlayData)
            publisher.afterModelUpdate(target, oldInlayData, listOf(inlayData))
          }
          else {
            val renderer = DeclarativeInlayRenderer(inlayData, textMetricsStorage, inlayData.providerId, sourceId)
            val properties = InlayProperties()
              .priority((inlayData.position as EndOfLinePosition).priority)
              .relatesToPrecedingText(true)
              .disableSoftWrapping(false)
            val inlay = inlayModel.addAfterLineEndElement(plan.lineEndOffset, properties, renderer)
            if (inlay != null) {
              renderer.initInlay(inlay)
            }
          }
        }
        for (plan in preprocessedInlayData.inline) {
          val inlayData = plan.inlayData
          val target = plan.updateTarget
          if (target != null) {
            val oldInlayData = updateInlay(target, inlayData)
            publisher.afterModelUpdate(target, oldInlayData, listOf(inlayData))
          }
          else {
            val renderer = DeclarativeInlayRenderer(inlayData, textMetricsStorage, inlayData.providerId, sourceId)
            val inlay = with(inlayData.position as InlineInlayPosition) {
              inlayModel.addInlineElement(offset, relatedToPrevious, priority, renderer)
            }
            if (inlay != null) {
              renderer.initInlay(inlay)
            }
          }
        }
        for (plan in preprocessedInlayData.aboveLine) {
          val inlayData = plan.inlayData
          val target = plan.updateTarget
          if (target != null) {
            val oldInlayData = updateInlay(target, inlayData)
            publisher.afterModelUpdate(target, oldInlayData, inlayData)
          }
          else {
            val renderer = DeclarativeIndentedBlockInlayRenderer(
              inlayData,
              textMetricsStorage,
              // providerId must be equal in a single Plan.AboveLine (see #forEachRun)
              inlayData.first().providerId,
              sourceId,
              plan.initialIndentOffset
            )
            val inlay = inlayModel.addBlockElement(
              plan.smallestOffset, false, true, inlayData.first().aboveLinePosition().verticalPriority, renderer
            )
            if (inlay != null) {
              renderer.initInlay(inlay)
            }
          }
        }
        deleteNotPreservedInlays(offsetToExistingInlineInlays)
        deleteNotPreservedInlays(offsetToExistingEolInlays)
        deleteNotPreservedInlays(offsetToExistingBlockInlays)
      }

      DeclarativeInlayHintsPassFactory.updateModificationStamp(editor, project)
    }

    private fun deleteNotPreservedInlays(offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<*>>>) {
      for (inlays in offsetToExistingInlays.values) {
        for (inlay in inlays) {
          Disposer.dispose(inlay)
        }
      }
    }

    /** @return old inlay data */
    private fun <M> updateInlay(
      inlay: Inlay<out DeclarativeInlayRendererBase<M>>,
      inlayData: M,
    ): List<InlayData> {
      val oldInlayData = inlay.renderer.toInlayData(false)
      inlay.renderer.updateModel(inlayData)
      inlay.update()
      return oldInlayData
    }

    /** @return `true` if a suitable inlay was found and updated; `false` otherwise. */
    private inline fun <M> findSuitableInlayAndRemoveFromDeleteList(
      offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<out DeclarativeInlayRendererBase<M>>>>,
      groupKey: Int,
      require: (Inlay<out DeclarativeInlayRendererBase<M>>) -> Boolean,
    ): Inlay<out DeclarativeInlayRendererBase<M>>? {
      val inlays = offsetToExistingInlays.get(groupKey) ?: return null
      val iterator = inlays.iterator()
      while (iterator.hasNext()) {
        val existingInlay = iterator.next()
        if (require(existingInlay)) {
          iterator.remove()
          return existingInlay
        }
      }
      return null
    }

    /**
     * [document] must be unchanged from when [inlayData] were collected
     */
    @ApiStatus.Internal
    @RequiresReadLock
    fun preprocessCollectedInlayData(inlayData: List<InlayData>, document: Document): PreprocessedInlayData {
      val inlinePlans = mutableListOf<PreprocessedInlayData.Plan.Inline>()
      val eolPlans = mutableListOf<PreprocessedInlayData.Plan.Eol>()
      val aboveLineData = mutableListOf<InlayData>()

      for (inlayData in inlayData) {
        when (inlayData.position) {
          is AboveLineIndentedPosition -> aboveLineData.add(inlayData)
          is EndOfLinePosition -> {
            val offset = document.getLineEndOffset(inlayData.position.line)
            eolPlans.add(PreprocessedInlayData.Plan.Eol(inlayData, offset))
          }
          is InlineInlayPosition -> inlinePlans.add(PreprocessedInlayData.Plan.Inline(inlayData))
        }
      }

      // The sort must be stable, so that insertion order from providers is maintained. See the AboveLineIndentedPosition doc.
      aboveLineData.sortWith(compareBy<InlayData> { it.aboveLinePosition().offset }
                               .thenBy { it.providerId }
                               .thenBy { it.aboveLinePosition().verticalPriority }
                               .thenByDescending { it.aboveLinePosition().priority })
      val aboveLinePlans = mutableListOf<PreprocessedInlayData.Plan.AboveLine>()
      aboveLineData.forEachRun(document) { line, _, _, inlayData ->
        aboveLinePlans.add(
          PreprocessedInlayData.Plan.AboveLine(
            inlayData,
            line,
            DocumentUtil.getLineStartIndentedOffset(document, line),
            inlayData.first().aboveLinePosition().offset,
          )
        )
      }

      return PreprocessedInlayData(
        inlinePlans,
        eolPlans,
        aboveLinePlans
      )
    }

    /** A run ⇔ group of inlays with the same line, provider, and vertical priority.
     *
     *  This function assumes `this.all { it.position is AboveLineIndentedPosition }`,
     *  and that the list is sorted by the properties that define a run.
     */
    private inline fun List<InlayData>.forEachRun(
      document: Document,
      action: (line: Int,
               providerId: String,
               verticalPriority: Int,
               // non-empty
               inlayData: List<InlayData>) -> Unit
    ) {
      if (isEmpty()) return

      val initial = first()
      var line = document.getLineNumber(initial.aboveLinePosition().offset)
      var providerId = initial.providerId
      var verticalPriority = initial.aboveLinePosition().verticalPriority
      var runStart = 0 // inclusive
      var runEnd = 1 // exclusive

      val iter = this.withIndex().iterator()
      iter.next()
      while (iter.hasNext()) {
        val (index, item) = iter.next()
        val itemLine = document.getLineNumber(item.aboveLinePosition().offset)
        if (itemLine != line ||
            item.providerId != providerId ||
            item.aboveLinePosition().verticalPriority != verticalPriority) {
          // end of a run, apply it
          action(line, providerId, verticalPriority, this.subList(runStart, index))
          // set up a new run
          line = itemLine
          providerId = item.providerId
          verticalPriority = item.aboveLinePosition().verticalPriority
          runStart = index
        }
        runEnd = index + 1
      }
      if (runStart < runEnd) {
        action(line, providerId, verticalPriority, this.subList(runStart, runEnd))
      }
    }
  }

  private fun createCollector(provider: InlayHintsProvider): InlayHintsCollector? {
    return provider.createCollector(myFile, editor)
  }
}

private inline fun <Rend : DeclarativeInlayRendererBase<*>> groupRelevantExistingInlays(
  filter: (existingInlay: Inlay<out Rend>) -> Boolean,
  inlays: List<Inlay<out Rend>>,
  groupKey: (Inlay<*>) -> Int
): Int2ObjectOpenHashMap<SmartList<Inlay<out Rend>>> {
  val grouped = Int2ObjectOpenHashMap<SmartList<Inlay<out Rend>>>()
  for (inlay in inlays) {
    if (!filter(inlay)) continue
    val key = groupKey(inlay)
    grouped.computeIfAbsent(key, IntFunction { SmartList() }).add(inlay)
  }
  return grouped
}

private const val INLAY_BATCH_MODE_THRESHOLD = 1000

private fun InlayData.aboveLinePosition() = position as AboveLineIndentedPosition
