// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.emacs;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class DefaultEmacsProcessingHandler implements EmacsProcessingHandler {

  @NotNull
  @Override
  public Result changeIndent(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return Result.CONTINUE;
  }
}
