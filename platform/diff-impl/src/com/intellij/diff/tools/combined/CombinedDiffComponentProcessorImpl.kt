// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffInEditor
import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffEditorViewerListener
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.ErrorDiffTool
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorStateWithPreferredOpenMode
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.JComponent

private val LOG = logger<CombinedDiffComponentProcessor>()

@ApiStatus.Experimental
interface CombinedDiffManager {
  fun createProcessor(diffPlace: String? = null): CombinedDiffComponentProcessor

  companion object {
    fun getInstance(project: Project): CombinedDiffManager = project.service()
  }
}

@ApiStatus.Internal
class CombinedDiffComponentProcessorImpl(
  val model: CombinedDiffModel,
  goToChangeAction: AnAction?,
) : CombinedDiffComponentProcessor {

  override val disposable = Disposer.newCheckedDisposable()

  private val mainUi: CombinedDiffMainUI

  private var combinedViewer: CombinedDiffViewer?

  private val eventDispatcher = EventDispatcher.create(DiffEditorViewerListener::class.java)

  init {
    Disposer.register(disposable, model.ourDisposable)
    model.addListener(ModelListener(), disposable)

    mainUi = CombinedDiffMainUI(model, goToChangeAction)
    Disposer.register(disposable, mainUi)
    model.context.putUserData(COMBINED_DIFF_MAIN_UI, mainUi)

    combinedViewer = createCombinedViewer(true)

    model.cleanBlocks()
  }

  override val preferredFocusedComponent: JComponent? get() = mainUi.getPreferredFocusedComponent()
  override val component: JComponent get() = mainUi.getComponent()

  override val filesToRefresh: List<VirtualFile> get() = emptyList()
  override val embeddedEditors: List<Editor> get() = combinedViewer?.editors.orEmpty()

  override val context: DiffContext get() = model.context
  override val blocks: List<CombinedBlockProducer> get() = model.requests
  override fun setBlocks(requests: List<CombinedBlockProducer>) = model.setBlocks(requests)
  override fun cleanBlocks() = model.cleanBlocks()

  override fun fireProcessorActivated() = Unit
  override fun addListener(listener: DiffEditorViewerListener, disposable: Disposable?) {
    if (disposable != null) {
      eventDispatcher.addListener(listener, disposable)
    }
    else {
      eventDispatcher.addListener(listener)
    }
  }

  override fun setToolbarVerticalSizeReferent(component: JComponent) {
    mainUi.setToolbarVerticalSizeReferent(component)
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    val viewer = combinedViewer
    if (viewer == null) return FileEditorState.INSTANCE

    val textEditorProvider = TextEditorProvider.getInstance()
    return CombinedDiffEditorState(
      model.requests.map { it.id }.toSet(),
      // FULL editor states are requested for actions restoring editor state after close, not by navigation.
      // We only want to restore the exact block selection when restoring for navigation or undo.
      if (level != FileEditorStateLevel.FULL) viewer.getCurrentBlockId() else null,
      viewer.getCurrentDiffViewer()?.editors?.map { textEditorProvider.getStateImpl(null, it, level) } ?: listOf()
    )
  }

  override fun setState(state: FileEditorState) {
    if (state !is CombinedDiffEditorState) return

    val viewer = combinedViewer ?: return
    if (model.requests.map { it.id }.toSet() != state.currentBlockIds) return

    val textEditorProvider = TextEditorProvider.getInstance()
    state.activeEditorStates.zip(state.activeBlockId?.let(viewer::getDiffViewerForId)?.editors ?: listOf()) { st, editor ->
      textEditorProvider.setStateImpl(project = null, editor = editor, state = st, exactState = true)
    }

    if (state.activeBlockId != null) {
      viewer.selectDiffBlock(state.activeBlockId, true)
      viewer.scrollToCaret()
    }
  }

  private fun createCombinedViewer(initialFocusRequest: Boolean): CombinedDiffViewer? {
    val context = model.context
    if (!DiffUtil.isUserDataFlagSet(COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST, context)) {
      context.putUserData(COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST, initialFocusRequest)
    }
    val blocks = model.requests.toList()
    val blockToSelect = model.context.getUserData(COMBINED_DIFF_SCROLL_TO_BLOCK)
    if (blocks.isEmpty()) return null

    val blockState = BlockState(blocks.map { it.id }, blockToSelect ?: blocks.first().id).apply {
      addListener({ _, _ -> eventDispatcher.multicaster.onActiveFileChanged() }, disposable)
    }

    return CombinedDiffViewer(context, MyBlockListener(), blockState, mainUi.getUiState()).also { viewer ->
      Disposer.register(disposable, viewer)
      context.putUserData(COMBINED_DIFF_VIEWER_KEY, viewer)
      mainUi.setContent(viewer, blockState)
    }
  }

  private inner class ModelListener : CombinedDiffModelListener {
    override fun onModelReset() {
      combinedViewer?.let {
        Disposer.dispose(it)
      }
      model.context.putUserData(COMBINED_DIFF_VIEWER_KEY, null)

      combinedViewer = createCombinedViewer(false)
    }

    @RequiresEdt
    override fun onRequestsLoaded(blockId: CombinedBlockId, request: DiffRequest) {
      val viewer = combinedViewer ?: return
      buildBlockContent(mainUi, model.context, request, blockId)?.let { newContent ->
        viewer.updateBlockContent(newContent)
        request.onAssigned(true)
      }

      viewer.contentChanged()
    }

    @RequiresEdt
    override fun onRequestContentsUnloaded(requests: Map<CombinedBlockId, DiffRequest>) {
      val viewer = combinedViewer ?: return

      for ((blockId, request) in requests) {
        viewer.replaceBlockWithPlaceholder(blockId)
        request.onAssigned(false)
      }
    }
  }

  private inner class MyBlockListener : BlockListener {

    override fun blocksHidden(blockIds: Collection<CombinedBlockId>) {
      model.unloadRequestContents(blockIds)
    }

    override fun blocksVisible(blockIds: Collection<CombinedBlockId>) {
      model.loadRequestContents(blockIds)
    }
  }

  companion object {
    private fun buildBlockContent(
      mainUi: CombinedDiffMainUI,
      context: DiffContext,
      request: DiffRequest,
      blockId: CombinedBlockId,
    ): CombinedDiffBlockContent? {
      val diffSettings = DiffSettingsHolder.DiffSettings.getSettings(context.getUserData(DiffUserDataKeys.PLACE))
      val diffTools = DiffManagerEx.getInstance().diffTools
      request.putUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF, true)
      context.getUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE)?.let { request.putUserData(DiffUserDataKeysEx.SCROLL_TO_CHANGE, it) }

      val orderedDiffTools = getOrderedDiffTools(diffSettings, diffTools, mainUi.isUnified())

      val diffTool = orderedDiffTools
                       .filter { it.canShow(context, request) }
                       .toList()
                       .let(DiffUtil::filterSuppressedTools)
                       .firstOrNull() ?: ErrorDiffTool.INSTANCE

      val childViewer = diffTool
                          ?.let { findSubstitutor(it, context, request) }
                          ?.createComponent(context, request)
                        ?: return null

      notifyDiffViewerCreated(childViewer, context, request)

      return CombinedDiffBlockContent(childViewer, blockId)
    }

    private fun buildLoadingBlockContent(blockId: CombinedBlockId, size: Dimension? = null): CombinedDiffBlockContent {
      return CombinedDiffBlockContent(CombinedDiffLoadingBlock(size), blockId)
    }

    private fun findSubstitutor(tool: FrameDiffTool, context: DiffContext, request: DiffRequest): FrameDiffTool {
      return DiffUtil.findToolSubstitutor(tool, context, request) as? FrameDiffTool ?: tool
    }

    private fun getOrderedDiffTools(
      diffSettings: DiffSettingsHolder.DiffSettings,
      diffTools: List<DiffTool>,
      isUnifiedView: Boolean,
    ): List<FrameDiffTool> {
      return DiffRequestProcessor.getToolOrderFromSettings(diffSettings, diffTools).asSequence()
        .filterIsInstance<FrameDiffTool>()
        .sortedBy {
          val isUnifiedTool: Boolean = it.toolType == DiffToolType.Unified
          if (isUnifiedView == isUnifiedTool) -1 else 0
        }
        .toList()
    }

    private fun notifyDiffViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
      DiffExtension.EP_NAME.forEachExtensionSafe { extension ->
        try {
          extension.onViewerCreated(viewer, context, request)
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
  }
}

@ApiStatus.Experimental
internal data class CombinedDiffEditorState(
  val currentBlockIds: Set<CombinedBlockId>,
  val activeBlockId: CombinedBlockId?,
  val activeEditorStates: List<TextEditorState>,
) : FileEditorStateWithPreferredOpenMode {
  override val openMode: FileEditorManagerImpl.OpenMode?
    get() = if (!isDiffInEditor) FileEditorManagerImpl.OpenMode.NEW_WINDOW else null

  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is CombinedDiffEditorState &&
           (currentBlockIds != otherState.currentBlockIds ||
            (activeBlockId == otherState.activeBlockId &&
             activeEditorStates.zip(otherState.activeEditorStates).all { (l, r) -> l.canBeMergedWith(r, level) }))
  }
}

@ApiStatus.Experimental
interface CombinedDiffComponentProcessor : DiffEditorViewer {
  val blocks: List<CombinedBlockProducer>

  /**
   * Updates current model with the new requests
   */
  fun setBlocks(requests: List<CombinedBlockProducer>)
  fun cleanBlocks()
}
