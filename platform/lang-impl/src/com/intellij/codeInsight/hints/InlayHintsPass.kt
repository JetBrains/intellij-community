// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.codeInsight.daemon.impl.Divider
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.concurrency.JobLauncher
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Predicates
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiElement
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentIntObjectMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.jetbrains.annotations.ApiStatus
import java.util.stream.IntStream

@ApiStatus.Internal
class InlayHintsPass(
  private val rootElement: PsiElement,
  private val enabledCollectors: List<CollectorWithSettings<out Any>>,
  private val editor: Editor,
  private val priorityRange: ProperTextRange,
  private val sharedSink: InlayHintsSinkImpl,
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true), DumbAware {

  override fun doCollectInformation(progress: ProgressIndicator) {
    if (!HighlightingLevelManager.getInstance(myFile.project).shouldHighlight(myFile)) return
    if (enabledCollectors.isEmpty()) return

    val allDivided = mutableListOf<Divider.DividedElements>()
    progress.checkCanceled()

    Divider.divideInsideAndOutsideAllRoots(myFile, myFile.textRange,
                                           priorityRange,
                                           Predicates.alwaysTrue(),
                                           CommonProcessors.CollectProcessor(allDivided))
    val elementsInside = allDivided.flatMap(Divider.DividedElements::inside)
    val elementsOutside = allDivided.flatMap(Divider.DividedElements::outside)
    val skippedCollectors = ConcurrentCollectionFactory.createConcurrentSet<Int>()

    if (!JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
        (elementsInside + elementsOutside),
        progress,
        true,
        false,
        Processor { element ->
          for (collectorInd in enabledCollectors.indices.minus(skippedCollectors)) {
            val collector = enabledCollectors[collectorInd]
            if (!collector.collectHints(element, editor)) {
              skippedCollectors.add(collectorInd)
              continue
            }
            progress.checkCanceled()
          }
          true
        }
      )) {
      throw ProcessCanceledException()
    }

  }

  override fun doApplyInformationToEditor() {
    val positionKeeper = EditorScrollingPositionKeeper(editor)
    positionKeeper.savePosition()
    applyCollected(sharedSink.complete(), rootElement, editor)
    positionKeeper.restorePosition(false)
    if (rootElement === myFile) {
      InlayHintsPassFactoryInternal.putCurrentModificationStamp(myEditor, myFile)
    }
  }

  companion object {
    private const val BULK_CHANGE_THRESHOLD = 1000
    private val MANAGED_KEY = Key.create<Boolean>("managed.inlay")
    private val PLACEHOLDER_KEY = Key.create<Boolean>("inlay.placeholder")

    internal fun applyCollected(hints: HintsBuffer?, element: PsiElement, editor: Editor, isPlaceholder: Boolean = false) {
      val startOffset = element.textOffset
      val endOffset = element.textRange.endOffset
      val inlayModel = editor.inlayModel

      val existingInlineInlays = inlayModel.getInlineElementsInRange(startOffset, endOffset, InlineInlayRenderer::class.java)
      val existingAfterLineEndInlays = inlayModel.getAfterLineEndElementsInRange(startOffset, endOffset, InlineInlayRenderer::class.java)
      val existingBlockInlays: List<Inlay<out BlockInlayRenderer>> = inlayModel.getBlockElementsInRange(startOffset, endOffset,
                                                                                                        BlockInlayRenderer::class.java)
      val existingBlockAboveInlays = mutableListOf<Inlay<out PresentationContainerRenderer<*>>>()
      val existingBlockBelowInlays = mutableListOf<Inlay<out PresentationContainerRenderer<*>>>()
      for (inlay in existingBlockInlays) {
        when (inlay.placement) {
          Inlay.Placement.ABOVE_LINE -> existingBlockAboveInlays
          Inlay.Placement.BELOW_LINE -> existingBlockBelowInlays
          else -> throw IllegalStateException()
        }.add(inlay)
      }

      val isBulk = shouldBeBulk(hints, existingInlineInlays, existingBlockAboveInlays, existingBlockBelowInlays)
      val factory = PresentationFactory(editor)
      inlayModel.execute(isBulk) {
        updateOrDispose(existingInlineInlays, hints, Inlay.Placement.INLINE, factory, editor)
        updateOrDispose(existingAfterLineEndInlays, hints, Inlay.Placement.INLINE /* 'hints' consider them as INLINE*/, factory, editor)
        updateOrDispose(existingBlockAboveInlays, hints, Inlay.Placement.ABOVE_LINE, factory, editor)
        updateOrDispose(existingBlockBelowInlays, hints, Inlay.Placement.BELOW_LINE, factory, editor)
        if (hints != null) {
          addInlineHints(hints, inlayModel)
          addBlockHints(factory, inlayModel, hints.blockAboveHints, true, isPlaceholder)
          addBlockHints(factory, inlayModel, hints.blockBelowHints, false, isPlaceholder)
        }
      }
    }


    private fun postprocessInlay(inlay: Inlay<out PresentationContainerRenderer<*>>, isPlaceholder: Boolean) {
      inlay.renderer.setListener(InlayContentListener(inlay))
      inlay.putUserData(MANAGED_KEY, true)
      if (isPlaceholder) inlay.putUserData(PLACEHOLDER_KEY, true)
    }


    private fun addInlineHints(hints: HintsBuffer, inlayModel: InlayModel) {
      for (entry in hints.inlineHints.entrySet()) {
        val renderer = InlineInlayRenderer(entry.value)

        val toBePlacedAtTheEndOfLine = entry.value.any { it.constraints?.placedAtTheEndOfLine ?: false }
        val isRelatedToPrecedingText = entry.value.all { it.constraints?.relatesToPrecedingText ?: false }
        val inlay = if (toBePlacedAtTheEndOfLine) {
          inlayModel.addAfterLineEndElement(entry.key, true, renderer)
        } else {
          inlayModel.addInlineElement(entry.key, isRelatedToPrecedingText, renderer)
        }

        inlay?.let { postprocessInlay(it, false) }
      }
    }

    private fun addBlockHints(
      factory: PresentationFactory,
      inlayModel: InlayModel,
      map: ConcurrentIntObjectMap<MutableList<ConstrainedPresentation<*, BlockConstraints>>>,
      showAbove: Boolean,
      isPlaceholder: Boolean
    ) {
      for (entry in map.entrySet()) {
        val presentations = entry.value
        val constraints = presentations.first().constraints
        val inlay = inlayModel.addBlockElement(
          entry.key,
          constraints?.relatesToPrecedingText ?: true,
          showAbove,
          constraints?.priority ?: 0,
          BlockInlayRenderer(factory, presentations)
        ) ?: break
        postprocessInlay(inlay, isPlaceholder)
        if (!showAbove) {
          break
        }
      }
    }

    private fun shouldBeBulk(hints: HintsBuffer?,
                             existingInlineInlays: MutableList<Inlay<out InlineInlayRenderer>>,
                             existingBlockAboveInlays: MutableList<Inlay<out PresentationContainerRenderer<*>>>,
                             existingBlockBelowInlays: MutableList<Inlay<out PresentationContainerRenderer<*>>>): Boolean {
      val totalChangesCount = when {
        hints != null -> estimateChangesCountForPlacement(existingInlineInlays.offsets(), hints, Inlay.Placement.INLINE) +
                         estimateChangesCountForPlacement(existingBlockAboveInlays.offsets(), hints, Inlay.Placement.ABOVE_LINE) +
                         estimateChangesCountForPlacement(existingBlockBelowInlays.offsets(), hints, Inlay.Placement.BELOW_LINE)
        else -> existingInlineInlays.size + existingBlockAboveInlays.size + existingBlockBelowInlays.size
      }
      return totalChangesCount > BULK_CHANGE_THRESHOLD
    }

    private fun List<Inlay<*>>.offsets(): IntStream = stream().mapToInt { it.offset }

    private fun updateOrDispose(existing: List<Inlay<out PresentationContainerRenderer<*>>>,
                                hints: HintsBuffer?,
                                placement: Inlay.Placement,
                                factory: InlayPresentationFactory,
                                editor: Editor
    ) {
      for (inlay in existing) {
        val managed = inlay.getUserData(MANAGED_KEY) ?: continue
        if (!managed) continue

        val offset = inlay.offset
        val elements = hints?.remove(offset, placement)
        if (elements == null) {
          if (inlay.getUserData(PLACEHOLDER_KEY) != true) Disposer.dispose(inlay)
          continue
        }
        else {
          inlay.putUserData(PLACEHOLDER_KEY, null)
          inlay.renderer.addOrUpdate(elements, factory, placement, editor)
        }
      }
    }

    /**
     *  Estimates count of changes (removal, addition) for inlays
     */
    fun estimateChangesCountForPlacement(existingInlayOffsets: IntStream, collected: HintsBuffer, placement: Inlay.Placement): Int {
      var count = 0
      val offsetsWithExistingHints = IntOpenHashSet()
      for (offset in existingInlayOffsets) {
        if (!collected.contains(offset, placement)) {
          count++
        }
        else {
          offsetsWithExistingHints.add(offset)
        }
      }
      val elementsToCreate = collected.countDisjointElements(offsetsWithExistingHints, placement)
      count += elementsToCreate
      return count
    }

    private fun <Constraints : Any> PresentationContainerRenderer<Constraints>.addOrUpdate(
      new: List<ConstrainedPresentation<*, *>>,
      factory: InlayPresentationFactory,
      placement: Inlay.Placement,
      editor: Editor
    ) {
      if (!isAcceptablePlacement(placement)) {
        throw IllegalArgumentException()
      }

      @Suppress("UNCHECKED_CAST")
      addOrUpdate(new as List<ConstrainedPresentation<*, Constraints>>, editor, factory)
    }
  }
}