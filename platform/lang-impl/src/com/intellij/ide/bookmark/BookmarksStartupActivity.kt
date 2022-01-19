// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity.Background

internal class BookmarksStartupActivity : Background {

  override fun runActivity(project: Project) {
    val app = ApplicationManager.getApplication() ?: return
    if (app.isHeadlessEnvironment) return
    BookmarksManager.getInstance(project)
  }
}
