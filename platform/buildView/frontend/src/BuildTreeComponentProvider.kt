// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.ui.split.SplitComponentId
import com.intellij.ui.split.SplitComponentProvider
import kotlinx.coroutines.CoroutineScope

private class BuildTreeComponentProvider : SplitComponentProvider {
  override fun createComponent(project: Project, scope: CoroutineScope, id: SplitComponentId): ComponentContainer {
    return BuildTreeView(project, scope, id)
  }
}