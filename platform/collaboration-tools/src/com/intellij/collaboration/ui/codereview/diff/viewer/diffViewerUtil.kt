// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleOnesideDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Subscribe to [vmsFlow] and show components created via [componentFactory] as inlays on proper lines in viewer editors
 *
 * @param VM - inlay viemodel
 */
@ApiStatus.Experimental
fun <VM : Any> DiffViewerBase.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<DiffMappedValue<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent
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
private fun <VM : Any> SimpleOnesideDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<DiffMappedValue<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent
) {
  val viewerReady = viewerReadyFlow()
  val vmsByLine = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms
      .filter { it.side == side }
      .groupBy({ it.lineIndex }, { it.value })
  }
  editor.controlInlaysIn(cs, vmsByLine, vmKeyExtractor, componentFactory)
}

private fun <VM : Any> UnifiedDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<DiffMappedValue<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent
) {
  val viewerReady = viewerReadyFlow { isContentGood }
  val vmsByLine = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms
      .groupBy({ transferLineToOneside(it.side, it.lineIndex) }, { it.value })
      .filterKeys { it >= 0 }
  }
  editor.controlInlaysIn(cs, vmsByLine, vmKeyExtractor, componentFactory)
}

private fun <VM : Any> TwosideTextDiffViewer.controlInlaysIn(
  cs: CoroutineScope,
  vmsFlow: Flow<Collection<DiffMappedValue<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent
) {
  val viewerReady = viewerReadyFlow()

  val vmsByLine1 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms
      .filter { it.side == Side.LEFT }
      .groupBy({ it.lineIndex }, { it.value })
  }
  editor1.controlInlaysIn(cs, vmsByLine1, vmKeyExtractor, componentFactory)

  val vmsByLine2 = combine(viewerReady, vmsFlow) { ready, vms ->
    if (ready) vms else emptyList()
  }.map { vms ->
    vms
      .filter { it.side == Side.RIGHT }
      .groupBy({ it.lineIndex }, { it.value })
  }
  editor2.controlInlaysIn(cs, vmsByLine2, vmKeyExtractor, componentFactory)
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
}