// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.SmartList
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.function.IntFunction

class DeclarativeInlayHintsPass(
  private val rootElement: PsiElement,
  private val editor: Editor,
  private val providerInfos: List<InlayProviderPassInfo>,
  private val isPreview: Boolean,
  private val isProviderDisabled: Boolean = false
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true) {
  private val sinks = ArrayList<InlayTreeSinkImpl>()

  override fun doCollectInformation(progress: ProgressIndicator) {
    val ownCollectors = ArrayList<CollectionInfo<OwnBypassCollector>>()
    val sharedCollectors = ArrayList<CollectionInfo<SharedBypassCollector>>()
    for (providerInfo in providerInfos) {
      val provider = providerInfo.provider
      val sink = InlayTreeSinkImpl(providerInfo.providerId, providerInfo.optionToEnabled, isPreview, isProviderDisabled, provider.javaClass)
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
    applyInlayData(editor, myFile, inlayDatas = sinks.flatMap { it.finish() })
  }

  companion object {
    @RequiresEdt
    internal fun applyInlayData(editor: Editor, file: PsiFile, inlayDatas: List<InlayData>) {
      val inlayModel = editor.inlayModel
      val document = editor.document
      val existingInlineElements = inlayModel.getInlineElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
      val existingEolElements = inlayModel.getAfterLineEndElementsInRange(0, document.textLength, DeclarativeInlayRenderer::class.java)
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
        when (val position = inlayData.position) {
          is EndOfLinePosition -> {
            val lineEndOffset = editor.document.getLineEndOffset(position.line)
            val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingEolElements, inlayData, lineEndOffset)
            if (!updated) {
              val presentationList = InlayPresentationList(inlayData.tree, inlayData.hasBackground, inlayData.disabled,
                                                           createPayloads(inlayData), inlayData.providerClass, inlayData.tooltip)
              val renderer = DeclarativeInlayRenderer(presentationList, storage, inlayData.providerId, position)
              val inlay = inlayModel.addAfterLineEndElement(lineEndOffset, true, renderer)
              if (inlay != null) {
                renderer.setInlay(inlay)
              }
            }
          }
          is InlineInlayPosition -> {
            val updated = tryUpdateAndDeleteFromListInlay(offsetToExistingInlineElements, inlayData, position.offset)
            if (!updated) {
              val presentationList = InlayPresentationList(inlayData.tree, inlayData.hasBackground, inlayData.disabled,
                                                           createPayloads(inlayData), inlayData.providerClass, inlayData.tooltip)
              val renderer = DeclarativeInlayRenderer(presentationList, storage, inlayData.providerId, position)
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

      DeclarativeInlayHintsPassFactory.updateModificationStamp(editor, file)
    }

    private fun createPayloads(inlayData: InlayData) =
      inlayData.payloads?.associate { it.payloadName to it.payload }

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
          renderer.updateState(inlayData.tree, inlayData.disabled, inlayData.hasBackground)
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