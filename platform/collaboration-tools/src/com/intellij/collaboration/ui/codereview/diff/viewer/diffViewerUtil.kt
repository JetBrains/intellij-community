// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.codereview.editor.controlInlaysIn
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Subscribe to [vmsFlow] and show components created via [componentFactory] as inlays on proper lines in viewer editors
 *
 * @param VM - inlay viemodel
 */
@ApiStatus.Experimental
fun <VM : DiffMapped> DiffViewerBase.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
) {
  when (this) {
    is SimpleOnesideDiffViewer -> {
      controlInlaysIn(cs, vmsFlow, vmKeyExtractor, componentFactory)
    }
    is UnifiedDiffViewer -> {
      controlInlaysIn(cs, vmsFlow, vmKeyExtractor, componentFactory)
    }
    is TwosideTextDiffViewer -> {
      controlInlaysIn(cs, vmsFlow, vmKeyExtractor, componentFactory)
    }
    else -> return
  }
}


@ApiStatus.Experimental
private fun <VM : DiffMapped> SimpleOnesideDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
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
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { componentFactory(it.vm) })
}

private fun <VM : DiffMapped> UnifiedDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
) {
  val viewerReady = viewerReadyFlow { isContentGood }
  val vmsForEditor = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        transferLineToOneside(loc.first, loc.second).takeIf { it >= 0 }
      }
    }
  }
  editor.controlInlaysIn(cs, vmsForEditor, { vmKeyExtractor(it.vm) }, { componentFactory(it.vm) })
}

private fun <VM : DiffMapped> TwosideTextDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<VM>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: CoroutineScope.(VM) -> JComponent
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
  editor1.controlInlaysIn(cs, vmsForEditor1, { vmKeyExtractor(it.vm) }, { componentFactory(it.vm) })

  val vmsForEditor2 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms.map { vm ->
      Wrapper(vm) { loc ->
        loc.takeIf { it.first == Side.RIGHT }?.second
      }
    }
  }
  editor2.controlInlaysIn(cs, vmsForEditor2, { vmKeyExtractor(it.vm) }, { componentFactory(it.vm) })
}

private fun <V : DiffViewerBase> V.viewerReadyFlow(
  isViewerGood: V.() -> Boolean = { true }
): Flow<Boolean> = callbackFlow {
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