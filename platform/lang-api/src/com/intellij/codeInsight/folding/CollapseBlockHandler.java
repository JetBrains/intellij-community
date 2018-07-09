/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.folding;

import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface CollapseBlockHandler {
  LanguageExtension<CollapseBlockHandler> EP_NAME = new LanguageExtension<>("com.intellij.codeInsight.folding.collapseBlockHandler");

  void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file);
}
