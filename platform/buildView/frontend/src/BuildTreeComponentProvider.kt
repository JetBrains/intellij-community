// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.BuildTreeSplitComponentBinding
import com.intellij.build.BuildViewId
import com.intellij.openapi.project.Project
import com.intellij.ui.split.SplitComponentBinding
import com.intellij.ui.split.SplitComponentProvider
import kotlinx.coroutines.CoroutineScope
import javax.swing.JComponent

internal class BuildTreeComponentProvider : SplitComponentProvider<BuildViewId> {
  override val binding: SplitComponentBinding<BuildViewId> = BuildTreeSplitComponentBinding

  override fun createComponent(project: Project, scope: CoroutineScope, modelId: BuildViewId): JComponent {
    return BuildTreeView(project, scope, modelId)
  }
}