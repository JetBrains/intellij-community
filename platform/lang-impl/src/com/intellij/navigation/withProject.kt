// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation

import com.intellij.navigation.finder.ProjectFinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode

suspend fun withProject(
    parameters: Map<String, String?>,
    block: suspend (project: Project) -> String?
): String? {
  val project =
      when (val result = ProjectFinder().find(parameters)) {
        is ProjectFinder.FindResult.Success -> result.project
        is ProjectFinder.FindResult.Error -> return result.message
      }

  project.waitForSmartMode()

  return block(project)
}
