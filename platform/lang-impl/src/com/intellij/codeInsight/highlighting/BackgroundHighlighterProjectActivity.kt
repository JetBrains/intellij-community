// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class BackgroundHighlighterProjectActivity : ProjectActivity {
  @JvmField val impl = BackgroundHighlighter()

  init {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment && !app.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!IdentifierHighlighterPassFactory.isEnabled()) {
      return
    }

    blockingContext {
      impl.runActivity(project)
    }
  }
}