// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaTextBlockIndentPassFactory implements TextEditorHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {

  @Nullable
  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    PsiJavaFile javaFile = ObjectUtils.tryCast(file, PsiJavaFile.class);
    if (javaFile == null) return null;
    if (!StringContentIndentUtil.isDocumentUpdated(editor)) return null;
    return new JavaTextBlockIndentPass(file.getProject(), editor, javaFile);
  }

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    registrar.registerTextEditorHighlightingPass(this,
                                                 TextEditorHighlightingPassRegistrar.Anchor.BEFORE, Pass.UPDATE_FOLDING,
                                                 false, false);
  }
}
