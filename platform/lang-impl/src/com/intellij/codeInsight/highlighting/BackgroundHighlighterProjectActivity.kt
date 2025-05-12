// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting

import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPassFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class BackgroundHighlighterProjectActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment && !app.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (IdentifierHighlighterPassFactory.isEnabled()) {
      val perProjectDisposable = project.serviceAsync<BackgroundHighlighterPerProject>()
      serviceAsync<BackgroundHighlighter>().registerListeners(project, perProjectDisposable, perProjectDisposable.coroutineScope)
    }
  }
}