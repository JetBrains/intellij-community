// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresEdt
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
  private val sinks = ArrayList<InlayTreeSinkImpl>()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val ownCollectors = ArrayList<CollectionInfo<OwnBypassCollector>>()
    val sharedCollectors = ArrayList<CollectionInfo<SharedBypassCollector>>()
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
  }

  private data class CollectionInfo<T>(
    val sink: InlayTreeSinkImpl,
    val collector: T
  )

  override fun doApplyInformationToEditor() {
    applyInlayData(editor, myFile.project, inlayDatas = sinks.flatMap { it.finish() }, passSourceId)
  }

  companion object {
    @ApiStatus.Internal
    val passSourceId: String = DeclarativeInlayHintsPass::class.java.name

    @RequiresEdt
    @ApiStatus.Internal
    fun applyInlayData(editor: Editor, project: Project, inlayDatas: List<InlayData>, sourceId: String) {
      val inlayModel = editor.inlayModel
      val document = editor.document
      val existingInlineElements = inlayModel
        .getInlineElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
        .filter { sourceId == it.renderer.getSourceId() }
      val existingEolElements = inlayModel
        .getAfterLineEndElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
        .filter { sourceId == it.renderer.getSourceId() }
      val offsetToExistingInlineElements = Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>() // either inlay or list of inlays
      val offsetToExistingEolElements = Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>() // either inlay or list of inlays
      for (inlineElement in existingInlineElements) {
        val inlaysAtOffset = offsetToExistingInlineElements.computeIfAbsent(inlineElement.offset, IntFunction { SmartList() })
        inlaysAtOffset.add(inlineElement)
      }
      for (eolElement in existingEolElements) {
        val inlaysAtOffset = offsetToExistingEolElements.computeIfAbsent(eolElement.offset, IntFunction { SmartList() })
        inlaysAtOffset.add(eolElement)
      }
      val storage = InlayHintsUtils.getTextMetricStorage(editor)
      for (inlayData in inlayDatas) {
        if (inlayData.sourceId != sourceId) {
          throw IllegalStateException("Inconsistent sourceId=$sourceId, inlayData=$inlayData")
        }
        when (val position = inlayData.position) {
          is EndOfLinePosition -> {
            val lineEndOffset = editor.document.getLineEndOffset(position.line)
            val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingEolElements, inlayData, lineEndOffset)
            if (!updated) {
              val renderer = DeclarativeInlayRenderer(inlayData, storage, inlayData.providerId, sourceId)
              val inlay = inlayModel.addAfterLineEndElement(lineEndOffset, true, renderer)
              if (inlay != null) {
                renderer.setInlay(inlay)
              }
            }
          }
          is InlineInlayPosition -> {
            val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingInlineElements, inlayData, position.offset)
            if (!updated) {
              val renderer = DeclarativeInlayRenderer(inlayData, storage, inlayData.providerId, sourceId)
              val inlay = inlayModel.addInlineElement(position.offset, position.relatedToPrevious, position.priority, renderer)
              if (inlay != null) {
                renderer.setInlay(inlay)
              }
            }
          }
        }
      }

      deleteNotPreservedInlays(offsetToExistingInlineElements)
      deleteNotPreservedInlays(offsetToExistingEolElements)

      DeclarativeInlayHintsPassFactory.updateModificationStamp(editor, project)
    }

    private fun deleteNotPreservedInlays(offsetToExistingInlays: Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>) {
      for (inlays in offsetToExistingInlays.values) {
        for (inlay in inlays) {
          Disposer.dispose(inlay)
        }
      }
    }

    private fun tryUpdateAndDeleteFromListInlay(offsetToExistingInlays: Int2ObjectOpenHashMap<SmartList<Inlay<out DeclarativeInlayRenderer>>>,
                                                inlayData: InlayData,
                                                offset: Int): Boolean {
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
  }

  private fun createCollector(provider: InlayHintsProvider): InlayHintsCollector? {
    return provider.createCollector(myFile, editor)
  }
}