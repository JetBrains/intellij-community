// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.psiView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PsiViewerCodeFragmentExtension extends JavaPsiViewerExtension {
  @Override
  public @NotNull String getName() {
    return "Java Code Block";
  }

  @Override
  public @NotNull Icon getIcon() {
    return PlatformIcons.CLASS_INITIALIZER;
  }

  @Override
  public @NotNull PsiElement createElement(@NotNull Project project, @NotNull String text) {
    return getFactory(project).createCodeBlockFromText(text, null);
  }
}
