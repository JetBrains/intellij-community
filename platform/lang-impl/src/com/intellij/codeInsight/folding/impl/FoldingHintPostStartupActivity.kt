// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class FoldingHintPostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    serviceAsync<EditorFactory>().eventMulticaster.addEditorMouseMotionListener(FoldingHintMouseMotionListener(project), project)
  }
}