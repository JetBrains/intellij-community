// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Document
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.IndentedDeclarativeHintView
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
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
  private lateinit var preprocessedInlayData: PreprocessedInlayData

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

  @ApiStatus.Internal
  class PreprocessedInlayData internal constructor(
    internal val inline: List<InlayData>,
    internal val endOfLine: List<InlayData>,
    internal val aboveLine: List<InlayData>
  ) {
    companion object {
      val EMPTY = PreprocessedInlayData(emptyList(), emptyList(), emptyList())
    }
  }

  override fun doApplyInformationToEditor() {
    applyInlayData(editor, myFile.project, preprocessedInlayData, passSourceId)
  }

  companion object {
    @ApiStatus.Internal
    val passSourceId: String = DeclarativeInlayHintsPass::class.java.name

    @RequiresEdt
    @ApiStatus.Internal
    fun applyInlayData(editor: Editor, project: Project, preprocessedInlayData: PreprocessedInlayData, sourceId: String) {
      val inlayModel = editor.inlayModel
      val document = editor.document
      val existingInlineElements = inlayModel
        .getInlineElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
        .filter { sourceId == it.renderer.sourceId }
      val existingEolElements = inlayModel
        .getAfterLineEndElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
        .filter { sourceId == it.renderer.sourceId }
      val existingBlockElements = inlayModel
        .getBlockElementsInRange(0, document.textLength, DeclarativeIndentedBlockInlayRenderer::class.java)
        .filter { sourceId == it.renderer.sourceId }
      val offsetToExistingInlineElements = Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>() // either inlay or list of inlays
      val offsetToExistingEolElements = Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>() // either inlay or list of inlays
      val offsetToExistingBlockElements = Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeIndentedBlockInlayRenderer>>>() // either inlay or list of inlays
      for (inlineElement in existingInlineElements) {
        val inlaysAtOffset = offsetToExistingInlineElements.computeIfAbsent(inlineElement.offset, IntFunction { SmartList() })
        inlaysAtOffset.add(inlineElement)
      }
      for (eolElement in existingEolElements) {
        val inlaysAtOffset = offsetToExistingEolElements.computeIfAbsent(eolElement.offset, IntFunction { SmartList() })
        inlaysAtOffset.add(eolElement)
      }
      for (blockElement in existingBlockElements) {
        val inlaysAtLine = offsetToExistingBlockElements.computeIfAbsent(blockElement.offset, IntFunction { SmartList() })
        inlaysAtLine.add(blockElement)
      }
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
        val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingEolElements, inlayData, lineEndOffset)
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
        val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingInlineElements, inlayData, position.offset)
        if (!updated) {
          val renderer = DeclarativeInlayRenderer(inlayData, storage, inlayData.providerId, sourceId)
          val inlay = inlayModel.addInlineElement(position.offset, position.relatedToPrevious, position.priority, renderer)
          if (inlay != null) {
            renderer.initInlay(inlay)
          }
        }
      }
      for (inlayData in preprocessedInlayData.aboveLine) {
        ensureConsistentSourceId(inlayData)
        val position = inlayData.position as AboveLineIndentedPosition
        val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingBlockElements, inlayData, position.offset)
        if (!updated) {
          val (_, anchorOffset) = IndentedDeclarativeHintView.calcIndentAnchorOffset(position.offset, document)
          val renderer = DeclarativeIndentedBlockInlayRenderer(inlayData, storage, inlayData.providerId, sourceId, anchorOffset)
          val inlay = inlayModel.addBlockElement(position.offset, false, true, position.verticalPriority, renderer)
          if (inlay != null) {
            renderer.initInlay(inlay)
          }
        }
      }

      deleteNotPreservedInlays(offsetToExistingInlineElements)
      deleteNotPreservedInlays(offsetToExistingEolElements)
      deleteNotPreservedInlays(offsetToExistingBlockElements)

      DeclarativeInlayHintsPassFactory.updateModificationStamp(editor, project)
    }

    private fun deleteNotPreservedInlays(offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<*>>>) {
      for (inlays in offsetToExistingInlays.values) {
        for (inlay in inlays) {
          Disposer.dispose(inlay)
        }
      }
    }

    private fun tryUpdateAndDeleteFromListInlay(
      offsetToExistingInlays: Int2ObjectOpenHashMap<out SmartList<out Inlay<out DeclarativeInlayRendererBase>>>,
      inlayData: InlayData,
      offset: Int,
    ): Boolean {
      val inlays = offsetToExistingInlays.get(offset)
      if (inlays == null) return false
      val iterator = inlays.iterator()
      while (iterator.hasNext()) {
        val existingInlay = iterator.next()
        val renderer = existingInlay.renderer
        if (renderer.providerId == inlayData.providerId) {
          renderer.updateModel(inlayData)
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
      val positionToInlayData = inlayData.groupBy { it.position::class.java }
      return PreprocessedInlayData(
        positionToInlayData[InlineInlayPosition::class.java] ?: emptyList(),
        positionToInlayData[EndOfLinePosition::class.java] ?: emptyList(),
        positionToInlayData[AboveLineIndentedPosition::class.java] ?: emptyList(),
      )
    }
  }

  private fun createCollector(provider: InlayHintsProvider): InlayHintsCollector? {
    return provider.createCollector(myFile, editor)
  }
}