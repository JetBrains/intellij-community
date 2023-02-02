// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff.viewer

import com.intellij.collaboration.ui.codereview.diff.DiffMappedValue
import com.intellij.collaboration.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.collaboration.ui.codereview.diff.EditorLineInlaysController
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.swing.JComponent

class UnifiedDiffViewerInlaysController<VM : Any>(
  parentCs: CoroutineScope,
  vmsFlow: Flow<List<DiffMappedValue<VM>>>,
  vmKeyExtractor: (VM) -> Any,
  componentFactory: (CoroutineScope, VM) -> JComponent,
  viewer: UnifiedDiffViewer
) {

  private val cs = parentCs.childScope(Dispatchers.Main)

  private val viewerReady = DiffViewerUtil.createViewerReadyFlow(cs, viewer, UnifiedDiffViewer::isContentGood)

  init {
    val vmsByLine = combine(viewerReady, vmsFlow) { ready, vms ->
      if (ready) vms else emptyList()
    }.map { vms ->
      vms
        .groupBy({ viewer.transferLineToOneside(it.side, it.lineIndex) }, { it.value })
        .filterKeys { it >= 0 }
    }

    val inlaysManager = EditorComponentInlaysManager(viewer.editor as EditorImpl)
    EditorLineInlaysController(cs, vmsByLine, vmKeyExtractor, componentFactory, inlaysManager)
  }
}
