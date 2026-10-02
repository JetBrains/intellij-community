// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class TemplateSubstitutionContext {
  private final @NotNull Project myProject;
  private final @NotNull ModNavigator myNavigator;

  @ApiStatus.Internal
  public TemplateSubstitutionContext(@NotNull Project project, @NotNull ModNavigator navigator) {
    myProject = project;
    myNavigator = navigator;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  /**
   * @return offset in the {@link #getDocument() document} where template is going to be inserted
   */
  public int getOffset() {
    return myNavigator.getCaretOffset();
  }

  public @NotNull PsiFile getPsiFile() {
    return myNavigator.getPsiFile();
  }

  /**
   * @return document prepared for live template insertion
   * @apiNote document may differ from the text of the {@link #getPsiFile() psiFile}, e.g. selected text is already removed from the editor.
   */
  public @NotNull Document getDocument() {
    return myNavigator.getDocument();
  }
}
