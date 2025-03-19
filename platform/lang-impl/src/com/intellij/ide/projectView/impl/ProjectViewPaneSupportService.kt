// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

@Service(Service.Level.PROJECT)
internal class ProjectViewPaneSupportService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun createProjectViewPaneSupport(
    pane: AbstractProjectViewPaneWithAsyncSupport,
    treeStructure: AbstractTreeStructure,
    comparator: java.util.Comparator<NodeDescriptor<*>>,
  ): ProjectViewPaneSupport =
    if (Registry.`is`("ide.project.view.coroutines", false)) {
      val scope = coroutineScope.childScope("ProjectViewPaneSupport id=${pane.id}, subId=${pane.subId}")
      Disposer.register(pane, Disposable { scope.cancel() })
      CoroutineProjectViewSupport(pane, project, scope, treeStructure, comparator)
    }
    else {
      AsyncProjectViewSupport(pane, project, treeStructure, comparator)
    }
}
