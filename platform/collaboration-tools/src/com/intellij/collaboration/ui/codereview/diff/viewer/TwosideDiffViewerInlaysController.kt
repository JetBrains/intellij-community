// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.collaboration.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.swing.JComponent

class TwosideDiffViewerInlaysController<VM : Any>(
  parentCs: CoroutineScope,
  private val vmsFlow: Flow<List<DiffMappedValue<VM>>>,
  private val vmKeyExtractor: (VM) -> Any,
  private val componentFactory: (CoroutineScope, VM) -> JComponent,
  viewer: TwosideTextDiffViewer
) {

  private val cs = parentCs.childScope(Dispatchers.Main)

  private val viewerReady = DiffViewerUtil.createViewerReadyFlow(cs, viewer)

  init {
    installController(viewer.editor1 as EditorImpl, Side.LEFT)
    installController(viewer.editor2 as EditorImpl, Side.RIGHT)
  }

  private fun installController(editor: EditorImpl, side: Side) {
    val vmsByLine = combine(viewerReady, vmsFlow) { ready, vms ->
      if (ready) vms else emptyList()
    }.map { vms ->
      vms
        .filter { it.side == side }
        .groupBy({ it.lineIndex }, { it.value })
    }

    val inlaysManager = EditorComponentInlaysManager(editor)
    EditorLineInlaysController(cs, vmsByLine, vmKeyExtractor, componentFactory, inlaysManager)
  }
}
