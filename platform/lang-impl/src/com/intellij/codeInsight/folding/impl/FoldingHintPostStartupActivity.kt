// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

private class FoldingHintPostStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    EditorFactory.getInstance().eventMulticaster.addEditorMouseMotionListener(FoldingHintMouseMotionListener(project), project)
  }
}