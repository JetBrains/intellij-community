// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

internal class BookmarksStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    BookmarksManager.getInstance(project)
  }
}
