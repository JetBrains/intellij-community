// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

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
  rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
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
  rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
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
  rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
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
  rendererFactory: CoroutineScope.(VM) -> CodeReviewComponentInlayRenderer
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
@ApiStatus.Internal
fun <I : CodeReviewInlayModel> DiffViewerBase.controlReviewIn(
  cs: CoroutineScope,
  modelFactory: CoroutineScope.(locationToLine: (DiffLineLocation) -> Int?, lineToLocation: (Int) -> DiffLineLocation?) -> CodeReviewEditorModel<I>,
  rendererFactory: CoroutineScope.(I) -> CodeReviewComponentInlayRenderer
) {
  val viewer = this
  cs.launchNow(Dispatchers.Main) {
    viewerReadyFlow().collectLatest {
      if (it) coroutineScope {
        val currentCs = this
        when (viewer) {
          is SimpleOnesideDiffViewer -> {
            val model = modelFactory(
              { loc -> loc.takeIf { it.first == viewer.side }?.second },
              { lineIdx -> DiffLineLocation(viewer.side, lineIdx) }
            )
            viewer.editor.controlInlaysIn(currentCs, model.inlays, CodeReviewInlayModel::key) { rendererFactory(it) }
            CodeReviewEditorGutterControlsRenderer.setupIn(currentCs, model, viewer.editor)
          }
          is UnifiedDiffViewer -> {
            val model = modelFactory(
              { (side, lineIdx) -> viewer.transferLineToOneside(side, lineIdx).takeIf { it >= 0 } },
              { lineIdx ->
                val (indices, side) = viewer.transferLineFromOneside(lineIdx)
                side.select(indices).takeIf { it >= 0 }?.let { side to it }
              }
            )
            viewer.editor.controlInlaysIn(currentCs, model.inlays, CodeReviewInlayModel::key) { rendererFactory(it) }
            CodeReviewEditorGutterControlsRenderer.setupIn(currentCs, model, viewer.editor)
          }
          is TwosideTextDiffViewer -> {
            val modelLeft = modelFactory(
              { (side, lineIdx) -> lineIdx.takeIf { side == Side.LEFT } },
              { lineIdx -> DiffLineLocation(Side.LEFT, lineIdx) }
            )
            viewer.editor1.controlInlaysIn(currentCs, modelLeft.inlays, CodeReviewInlayModel::key) { rendererFactory(it) }
            CodeReviewEditorGutterControlsRenderer.setupIn(currentCs, modelLeft, viewer.editor1)


            val modelRight = modelFactory(
              { (side, lineIdx) -> lineIdx.takeIf { side == Side.RIGHT } },
              { lineIdx -> DiffLineLocation(Side.RIGHT, lineIdx) }
            )
            viewer.editor2.controlInlaysIn(currentCs, modelRight.inlays, CodeReviewInlayModel::key) { rendererFactory(it) }
            CodeReviewEditorGutterControlsRenderer.setupIn(currentCs, modelRight, viewer.editor2)
          }
        }
      }

    }
  }
}

private fun <V : DiffViewerBase> V.viewerReadyFlow(
): Flow<Boolean> = callbackFlow {
  val isViewerGood: V.() -> Boolean = {
    if (this is UnifiedDiffViewer) isContentGood else true
  }
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
}.flowOn(Dispatchers.Main)

interface DiffMapped {
  val location: Flow<DiffLineLocation?>
  val isVisible: Flow<Boolean>
}

private class Wrapper<VM : DiffMapped>(val vm: VM, val mapper: (DiffLineLocation) -> Int?) : EditorMapped {
  override val line: Flow<Int?> = vm.location.map { it?.let(mapper) }
  override val isVisible: Flow<Boolean> = vm.isVisible
}