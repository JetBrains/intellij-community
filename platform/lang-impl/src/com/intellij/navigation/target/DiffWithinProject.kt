// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.target

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.IdeBundle
import com.intellij.navigation.NavigationKeyPrefix
import com.intellij.navigation.finder.VirtualFileFinder
import com.intellij.navigation.getNavigationKeyValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiffWithinProject(
    private val project: Project,
    private val parameters: Map<String, String>,
) {
  suspend fun navigate(): String? {
    val revisionLeft =
        parameters.getNavigationKeyValue(NavigationKeyPrefix.REVISION_LEFT)
            ?: return IdeBundle.message(
                "jb.protocol.navigate.missing.parameter", NavigationKeyPrefix.REVISION_LEFT)
    val revisionRight =
        parameters.getNavigationKeyValue(NavigationKeyPrefix.REVISION_RIGHT)
            ?: return IdeBundle.message(
                "jb.protocol.navigate.missing.parameter", NavigationKeyPrefix.REVISION_RIGHT)

    val fileLeft =
        when (val result =
            VirtualFileFinder(
                    pathKey = NavigationKeyPrefix.PATH_LEFT,
                    revisionKey = NavigationKeyPrefix.REVISION_LEFT)
                .find(project, parameters)) {
          is VirtualFileFinder.FindResult.Success -> result.virtualFile
          is VirtualFileFinder.FindResult.Error -> return result.message
        }
    val fileRight =
        when (val result =
            VirtualFileFinder(
                    pathKey = NavigationKeyPrefix.PATH_RIGHT,
                    revisionKey = NavigationKeyPrefix.REVISION_RIGHT)
                .find(project, parameters)) {
          is VirtualFileFinder.FindResult.Success -> result.virtualFile
          is VirtualFileFinder.FindResult.Error -> return result.message
        }

    val name =
        if (fileLeft.name == fileRight.name) {
          fileLeft.name
        } else {
          "${fileLeft.name} → ${fileRight.name}"
        }

    withContext(Dispatchers.EDT) {
      val diffManager = DiffManager.getInstance()
      val diffContentFactory = DiffContentFactory.getInstance()

      diffManager.showDiff(
          project,
          SimpleDiffRequest(
              name,
              diffContentFactory.create(project, fileLeft),
              diffContentFactory.create(project, fileRight),
              revisionLeft,
              revisionRight,
          ))
    }

    return null
  }
}
