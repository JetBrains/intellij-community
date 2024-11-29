// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.util.HashingUtil
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vcs.history.VcsDiffUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Subscribe to [vmsFlow] and show inlays with renderers from [rendererFactory] on proper lines in viewer editors
 *
 * @param VM - inlay viemodel
 */
@ApiStatus.Experimental
fun <VM : DiffMapped> DiffViewerBase.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  when (this) {
    is SimpleOnesideDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    is UnifiedDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    is TwosideTextDiffViewer -> controlInlaysIn(cs, vmsFlow, vmKeyExtractor, rendererFactory)
    else -> return
  }
}


@ApiStatus.Experimental
private fun <VM : DiffMapped> SimpleOnesideDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()
  val vmsForEditor = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == side }?.second
      }
    }
  }
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

private fun <VM : DiffMapped> UnifiedDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()
  val vmsForEditor = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        transferLineToOneside(loc.first, loc.second).takeIf { it >= 0 }
      }
    }
  }
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

private fun <VM : DiffMapped> TwosideTextDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  rendererFactory: CodeReviewRendererFactory<VM>,
) {
  val viewerReady = viewerReadyFlow()

  val vmsForEditor1 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == Side.LEFT }?.second
      }
    }
  }
  editor1.controlInlaysIn(cs, vmsForEditor1, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })

  val vmsForEditor2 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == Side.RIGHT }?.second
      }
    }
  }
  editor2.controlInlaysIn(cs, vmsForEditor2, { vmKeyExtractor(it.vm) }, { rendererFactory(it.vm) })
}

/**
 * Create editor models for diff editors via [modelFactory] and show inlays and gutter controls
 * Inlays are created via [rendererFactory]
 *
 * @param M - editor inlays and controls model
 * @param I - inlay model
 */

@Deprecated("Using a suspend function is safer for threading",
            ReplaceWith("cs.launch { controlReview(modelFactory, modelKey, rendererFactory) }"))
fun <M : CodeReviewEditorModel<I>, I : CodeReviewInlayModel> DiffViewerBase.controlReviewIn(
  cs: CoroutineScope,
  modelFactory: CoroutineScope.(locationToLine: (DiffLineLocation) -> Int?, lineToLocation: (Int) -> DiffLineLocation?) -> M,
  modelKey: Key<M>,
  rendererFactory: CodeReviewRendererFactory<I>,
) {
  cs.launchNow { showCodeReview(modelFactory, modelKey, rendererFactory) }
}

/**
 * Create editor models for diff editors via [modelFactory] and show inlays and gutter controls
 * Inlays are created via [rendererFactory]
 *
 * @param M editor inlays and controls model
 * @param I inlay model
 * @param modelKey will be used to store model in editor user data keys
 */
@ApiStatus.Experimental
suspend fun <M, I> DiffViewerBase.showCodeReview(
  modelFactory: CoroutineScope.(locationToLine: (DiffLineLocation) -> Int?, lineToLocation: (Int) -> DiffLineLocation?) -> M,
  rendererFactory: RendererFactory<I, JComponent>,
): Nothing where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  showCodeReview(modelFactory, null, rendererFactory)
}

/**
 * Create editor models for diff editors via [modelFactory] and show inlays and gutter controls
 * Inlays are created via [rendererFactory]
 *
 * @param M editor inlays and controls model
 * @param I inlay model
 * @param modelKey will be used to store model in editor user data keys
 */
@ApiStatus.Experimental
suspend fun <M, I> DiffViewerBase.showCodeReview(
  modelFactory: CoroutineScope.(locationToLine: (DiffLineLocation) -> Int?, lineToLocation: (Int) -> DiffLineLocation?) -> M,
  modelKey: Key<M>? = null,
  rendererFactory: RendererFactory<I, JComponent>,
): Nothing where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  val viewer = this
  withContext(Dispatchers.Main + CoroutineName("Code review diff UI")) {
    supervisorScope {
      var prevJob: Job? = null
      viewerReadyFlow().collect {
        prevJob?.cancelAndJoinSilently()
        if (!it) return@collect

        when (viewer) {
          is SimpleOnesideDiffViewer -> {
            prevJob = launchNow {
              val model = modelFactory(
                { loc -> loc.takeIf { it.first == viewer.side }?.second },
                { lineIdx -> DiffLineLocation(viewer.side, lineIdx) }
              )
              viewer.editor.showCodeReview(model, modelKey, rendererFactory)
            }
          }
          is UnifiedDiffViewer -> {
            prevJob = launchNow {
              val model = modelFactory(
                { (side, lineIdx) -> viewer.transferLineToOnesideStrict(side, lineIdx).takeIf { it >= 0 } },
                { lineIdx ->
                  val (indices, side) = viewer.transferLineFromOneside(lineIdx)
                  side.select(indices).takeIf { it >= 0 }?.let { side to it }
                }
              )
              viewer.editor.showCodeReview(model, modelKey, rendererFactory)
            }
          }
          is TwosideTextDiffViewer -> {
            prevJob = launchNow {
              launchNow {
                val model = modelFactory(
                  { (side, lineIdx) -> lineIdx.takeIf { side == Side.LEFT } },
                  { lineIdx -> DiffLineLocation(Side.LEFT, lineIdx) }
                )
                viewer.editor1.showCodeReview(model, modelKey, rendererFactory)
              }
              launchNow {
                val model = modelFactory(
                  { (side, lineIdx) -> lineIdx.takeIf { side == Side.RIGHT } },
                  { lineIdx -> DiffLineLocation(Side.RIGHT, lineIdx) }
                )
                viewer.editor2.showCodeReview(model, modelKey, rendererFactory)
              }
            }
          }
        }
      }
    }
    awaitCancellation()
  }
}

private suspend fun <I, M> EditorEx.showCodeReview(model: M, modelKey: Key<M>?, rendererFactory: RendererFactory<I, JComponent>): Nothing
  where I : CodeReviewInlayModel, M : CodeReviewEditorModel<I> {
  val editor = this
  coroutineScope {
    launchNow {
      CodeReviewEditorGutterControlsRenderer.render(model, editor)
    }

    launchNow {
      renderInlays(model.inlays, HashingUtil.mappingStrategy(CodeReviewInlayModel::key)) { rendererFactory(it) }
    }

    if (modelKey != null) {
      putUserData(modelKey, model)
    }
    putUserData(CodeReviewCommentableEditorModel.KEY, model)
    try {
      awaitCancellation()
    }
    finally {
      putUserData(CodeReviewCommentableEditorModel.KEY, null)
      if (modelKey != null) {
        putUserData(modelKey, null)
      }
    }
  }
}

internal fun <V : DiffViewerBase> V.viewerReadyFlow(): Flow<Boolean> {
  val isViewerGood: V.() -> Boolean = { !hasPendingRediff() }
  return callbackFlow {
    val listener = object : DiffViewerListener() {
      // for now this utility is only used for constant diffs
      // uncomment if diff can actually be changed on rediff
      /*override fun onBeforeRediff() {
      trySend(false)
    }*/

      override fun onAfterRediff() {
        trySend(isViewerGood())
      }
    }
    addListener(listener)
    send(isViewerGood())
    awaitClose {
      removeListener(listener)
    }
  }.withInitial(isViewerGood()).flowOn(Dispatchers.Main).distinctUntilChanged()
}

interface DiffMapped {
  val location: Flow<DiffLineLocation?>
  val isVisible: Flow<Boolean>
}

private class Wrapper<VM : DiffMapped>(val vm: VM, val mapper: (DiffLineLocation) -> Int?) : EditorMapped {
  override val line: Flow<Int?> = vm.location.map { it?.let(mapper) }
  override val isVisible: Flow<Boolean> = vm.isVisible
}

/**
 * @see com.intellij.openapi.diff.impl.DiffTitleWithDetailsCustomizers
 * @see com.intellij.openapi.vcs.history.VcsDiffUtil.putFilePathsIntoChangeContext
 */
@Deprecated("Path of changed files is shown via DiffTitleFilePathCustomizer")
fun RefComparisonChange.buildChangeContext(): Map<Key<*>, Any> {
  val titleLeft = VcsDiffUtil.getRevisionTitle(revisionNumberBefore.toShortString(), filePathBefore, filePathAfter)
  val titleRight = VcsDiffUtil.getRevisionTitle(revisionNumberAfter.toShortString(), filePathAfter, null)

  val changeContext: MutableMap<Key<*>, Any> = mutableMapOf(
    DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE to titleLeft,
    DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE to titleRight
  )
  return changeContext
}