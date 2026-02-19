// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.CachedTreePresentationNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CachedNodeNavigatable(private val project: Project, private val node: CachedTreePresentationNode) : Navigatable {
  override fun navigationRequest(): NavigationRequest? {
    val path = node.data.extraAttributes?.get(ProjectViewNode.CACHED_FILE_PATH_KEY) ?: return null
    val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
    return NavigationRequests.getInstance().sourceNavigationRequest(project, file, 0, null)
  }
}
