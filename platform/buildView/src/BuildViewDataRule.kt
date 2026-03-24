// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView

import com.intellij.build.BackendMultipleBuildsView
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.platform.buildView.BuildDataKeys.BUILD_ID

internal class BuildViewDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val buildId = snapshot[BUILD_ID] ?: return
    val buildView = BackendMultipleBuildsView.getBuildView(buildId) ?: return
    buildView.eventView?.uiDataSnapshot(sink)
    buildView.uiDataSnapshot(sink)
  }
}