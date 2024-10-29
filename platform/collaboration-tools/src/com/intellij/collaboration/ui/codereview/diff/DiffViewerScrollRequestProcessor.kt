// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerChangeScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerLineScrollRequest
import com.intellij.collaboration.ui.codereview.diff.model.DiffViewerScrollRequest
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy

internal object DiffViewerScrollRequestProcessor {
  fun scroll(viewer: FrameDiffTool.DiffViewer, request: DiffViewerScrollRequest) {
    when (request) {
      is DiffViewerLineScrollRequest -> scroll(viewer, request.location)
      is DiffViewerChangeScrollRequest -> scroll(viewer, request.policy)
    }
  }

  fun scroll(viewer: FrameDiffTool.DiffViewer, location: DiffLineLocation) {
    val (side, line) = location
    when (viewer) {
      is OnesideTextDiffViewer -> viewer.scrollToLine(line)
      is SimpleDiffViewer -> viewer.scrollToLine(side, line)
      is UnifiedDiffViewer -> viewer.scrollToLine(side, line)
    }
  }

  private fun scroll(viewer: FrameDiffTool.DiffViewer, policy: ScrollToPolicy) {
    when (viewer) {
      is SimpleDiffViewer -> viewer.scrollToChange(policy)
      is UnifiedDiffViewer -> viewer.scrollToChange(policy)
    }
  }
}