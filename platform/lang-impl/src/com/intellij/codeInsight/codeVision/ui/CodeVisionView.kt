package com.intellij.codeInsight.codeVision.ui

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.*
import com.intellij.codeInsight.codeVision.ui.popup.CodeVisionPopup
import com.intellij.codeInsight.codeVision.ui.renderers.BlockCodeVisionListRenderer
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.codeInsight.codeVision.ui.renderers.InlineCodeVisionListRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.application
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reflection.usingTrueFlag


@Suppress("INACCESSIBLE_TYPE")
class CodeVisionView(val project: Project) {
  companion object {
    private val logger = Logger.getInstance(CodeVisionView::class.java)
  }

  private val projectModel = ProjectCodeVisionModel.getInstance(project)
  private val inlays = HashSet<Inlay<*>>()

  private val inlaysToDelete = HashSet<Inlay<*>>()
  private var delayInlayRemoval = false

  private fun isLensValid(lenses: Map<CodeVisionAnchorKind, List<CodeVisionEntry>>): Boolean = lenses.values.any { it.isNotEmpty() }

  fun runWithReusingLenses(action: () -> Unit) {
    application.assertIsDispatchThread()
    usingTrueFlag(CodeVisionView::delayInlayRemoval) {
      //inlaysToDelete are filled here in action() method
      action()
      //it only needs vertical keeper in case of block inlays
      val needsKeeper = inlaysToDelete.any { it.renderer is BlockCodeVisionListRenderer }
      val editors = inlaysToDelete.map { it.editor }.filter { !it.isDisposed }.distinct().toTypedArray()
      val keeper = CodeVisionVisualVerticalPositionKeeper(*editors)
      inlaysToDelete.forEach { Disposer.dispose(it) }
      inlaysToDelete.clear()
      if (needsKeeper)
        keeper.restoreOriginalLocation()
    }
  }

  fun addCodeLenses(
    lifetime: Lifetime,
    editor: Editor,
    anchoringRange: TextRange,
    lenses: Map<CodeVisionAnchorKind, List<CodeVisionEntry>>
  ): (Int) -> Unit {
    if (!isLensValid(lenses)) return {}

    CodeVisionSelectionController.install(editor as EditorImpl, projectModel)

    val rangeCodeVisionModel = RangeCodeVisionModel(project, editor, lenses, anchoringRange)

    val listInlays = mutableListOf<Inlay<*>>()
    for (lens in lenses) {
      val logicalPosition = editor.offsetToLogicalPosition(anchoringRange.startOffset)
      val inlay = when (if (lens.key == CodeVisionAnchorKind.Default) CodeVisionSettings.instance().defaultPosition else lens.key) {
        CodeVisionAnchorKind.Top -> {
          getOrCreateBlockInlay(editor, anchoringRange)
        }
        CodeVisionAnchorKind.Right -> {
          getOrCreateAfterLineEndInlay(editor, logicalPosition)
        }
        else -> error("Unsupported lens type")
      }
      listInlays.add(inlay)
      addCodeLenses(inlay, rangeCodeVisionModel, lens.value, lens.key, lifetime)
    }
    lifetime.onTermination {
      for (inlay in listInlays) {
        inlays.remove(inlay)
        rangeCodeVisionModel.inlays.remove(inlay)

        if (delayInlayRemoval)
          inlaysToDelete.add(inlay)
        else {
          logger.trace("Removing inlay at ${inlay.offset}")
          Disposer.dispose(inlay)
        }
      }
      updateSubscription()
    }
    return {
      CodeVisionPopup.showMorePopup(lifetime, project, editor, it, CodeVisionPopup.Disposition.CURSOR_POPUP_DISPOSITION,
                                    rangeCodeVisionModel)
    }
  }

  private fun getOrCreateAfterLineEndInlay(
    editor: EditorImpl,
    logicalPosition: LogicalPosition
  ): Inlay<InlineCodeVisionListRenderer> {
    val inlayModel = editor.inlayModel
    val lineEndOffset = editor.document.getLineEndOffset(logicalPosition.line)

    @Suppress("USELESS_CAST") // Cast of Inlay<raw> to Inaly<*> is not useless, KT-27831
    val existingInlay = inlayModel.getAfterLineEndElementsInRange(lineEndOffset, lineEndOffset)
      //potentially fixing DEXP-544825
      .singleOrNull { it.renderer is InlineCodeVisionListRenderer } as Inlay<*>?
    assert(existingInlay == null || inlaysToDelete.contains(
      existingInlay)) { "Inlay must be scheduled for deletion when reusing it; it isn't; bug?" }
    assert(
      existingInlay == null || existingInlay.renderer is InlineCodeVisionListRenderer) { "Reused inlay's renderer must be of proper type" }
    existingInlay?.let { inlaysToDelete.remove(it) }
    @Suppress("UNCHECKED_CAST") // checked above
    return existingInlay as Inlay<InlineCodeVisionListRenderer>? ?: run {
      //why do we need a keeper here if LineEndInlay does not shift lines
      //val keeper = CodeLensVisualVerticalPositionKeeper(editor)
      val inlay = inlayModel.addAfterLineEndElement(
        lineEndOffset, false,
        InlineCodeVisionListRenderer()
      )
      //keeper.restoreOriginalLocation()
      inlay
    }
  }

  private fun getOrCreateBlockInlay(
    editor: EditorImpl,
    anchoringRange: TextRange
  ): Inlay<BlockCodeVisionListRenderer> {
    val inlayModel = editor.inlayModel
    //rendering logic in BlockLensListRenderer finds the start offset of the line itself
    //inlayAnchor should just correspond to the anchor element
    val inlayAnchor = anchoringRange.startOffset

    @Suppress("USELESS_CAST") // Cast of Inlay<raw> to Inaly<*> is not useless, KT-27831
    val existingInlay = inlayModel.getBlockElementsInRange(inlayAnchor, inlayAnchor)
      .singleOrNull { it.renderer is BlockCodeVisionListRenderer } as Inlay<*>?
    assert(existingInlay == null || inlaysToDelete.contains(
      existingInlay)) { "Inlay must be scheduled for deletion when reusing it; it isn't; bug?" }
    assert(
      existingInlay == null || existingInlay.renderer is BlockCodeVisionListRenderer) { "Reused inlay's renderer must be of proper type" }
    existingInlay?.let { inlaysToDelete.remove(it) }
    if (existingInlay == null)
      logger.trace("Creating new block inlay at offset $inlayAnchor")
    @Suppress("UNCHECKED_CAST") // checked above

    return existingInlay as Inlay<BlockCodeVisionListRenderer>? ?: run {
      val keeper = CodeVisionVisualVerticalPositionKeeper(editor)

      val inlay = editor.inlayModel.addBlockElement(
        inlayAnchor, true, true,
        1, BlockCodeVisionListRenderer()
      )
      keeper.restoreOriginalLocation()
      inlay
    }

  }

  private fun addCodeLenses(
    inlay: Inlay<out CodeVisionRenderer>,
    rangeCodeVisionModel: RangeCodeVisionModel,
    list: List<CodeVisionEntry>,
    anchor: CodeVisionAnchorKind,
    lifetime: Lifetime
  ) {
    rangeCodeVisionModel.inlays.add(inlay)
    val lensData = CodeVisionListData(lifetime, projectModel, rangeCodeVisionModel, inlay, list, anchor)
    inlay.putUserData(CodeVisionListData.KEY, lensData)
    inlays.add(inlay)
    inlay.update()
    updateSubscription()
  }

  var subscriptionDef: LifetimeDefinition? = null

  fun setPerAnchorLimits(limits: Map<CodeVisionAnchorKind, Int>) {
    limits.forEach { _ -> projectModel.maxVisibleLensCount.putAll(limits) }
  }


  private fun updateSubscription() {
    if (inlays.isEmpty()) {
      if (subscriptionDef != null && subscriptionDef!!.isAlive) subscriptionDef!!.terminate()
      return
    }

    if (subscriptionDef == null || !subscriptionDef!!.isAlive) {
      subscriptionDef = project.createLifetime().createNested()
      subscribe(subscriptionDef!!.lifetime)
    }
  }

  private fun subscribe(lifetime: Lifetime) {
    projectModel.maxVisibleLensCount.advise(lifetime) {
      repaintInlays()
    }


  }

  private fun repaintInlays() {
    inlays.forEach { it.repaint() }
  }
}