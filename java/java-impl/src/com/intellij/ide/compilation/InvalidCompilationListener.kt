// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.compilation

import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.psi.util.ProjectIconsAccessor.isIdeaProject

internal class InvalidCompilationListener : CompilationStatusListener {
  override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
    val project = compileContext.getProject()

    if (!isIdeaProject(project)) {
      return
    }

    val errorMessages = compileContext.getMessages(CompilerMessageCategory.ERROR)
    if (errorMessages.isNotEmpty()) {
      InvalidCompilationTracker.getInstance(project).analyzeCompilerErrorsInBackground(errorMessages.toList())
    }
    else if (!aborted) {
      InvalidCompilationTracker.getInstance(project).compilationSucceeded()
    }
  }
}