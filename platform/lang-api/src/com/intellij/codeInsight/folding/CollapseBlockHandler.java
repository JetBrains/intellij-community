// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface CollapseBlockHandler {
  LanguageExtension<CollapseBlockHandler> EP_NAME = new LanguageExtension<>("com.intellij.codeInsight.folding.collapseBlockHandler");

  void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file);
}
