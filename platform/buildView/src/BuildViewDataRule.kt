// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BUILD_TREE_SELECTED_NODE
import com.intellij.build.BackendMultipleBuildsView
import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.findValue
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.platform.buildView.BuildDataKeys.BUILD_ID
import com.intellij.platform.buildView.BuildDataKeys.BUILD_VIEW_ID

internal class BuildViewDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val buildId = snapshot[BUILD_ID]
    if (buildId != null) {
      val buildView = BackendMultipleBuildsView.getBuildView(buildId)
      if (buildView != null) {
        buildView.eventView?.uiDataSnapshot(sink)
        buildView.uiDataSnapshot(sink)
      }
    }
    val buildViewId = snapshot[BUILD_VIEW_ID]
    val selectedTreeNode = snapshot[BUILD_TREE_SELECTED_NODE]
    if (selectedTreeNode != null && buildViewId != null && buildViewId.isLocal()) {
      val viewModel = buildViewId.findValue()
      if (viewModel != null) {
        sink[BuildTreeConsoleView.EXECUTION_NODE] = viewModel.getNodeById(selectedTreeNode.nodeId)
      }
    }
  }
}