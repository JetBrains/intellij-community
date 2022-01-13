// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocInfoGeneratorFactory {
  public static JavaDocInfoGeneratorFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaDocInfoGeneratorFactory.class);
  }

  protected JavaDocInfoGenerator createImpl(@NotNull Project project, @Nullable PsiElement element) {
    return new JavaDocInfoGenerator(
      project,
      element,
      JavaDocHighlightingManagerImpl.getInstance(),
      false,
      EditorSettingsExternalizable.getInstance().isDocSyntaxHighlightingEnabled());
  }

  protected JavaDocInfoGenerator createImpl(
    @NotNull Project project,
    @Nullable PsiElement element,
    @NotNull JavaDocHighlightingManager highlightingManager,
    boolean isGenerationForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    return new JavaDocInfoGenerator(project, element, highlightingManager, isGenerationForRenderedDoc, doSyntaxHighlighting);
  }

  @NotNull
  public static JavaDocInfoGenerator create(@NotNull Project project, @Nullable PsiElement element) {
    return getInstance().createImpl(project, element);
  }

  @NotNull
  public static JavaDocInfoGenerator create(
    @NotNull Project project,
    @Nullable PsiElement element,
    @NotNull JavaDocHighlightingManager highlightingManager,
    boolean isGenerationForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    return getInstance().createImpl(project, element, highlightingManager, isGenerationForRenderedDoc, doSyntaxHighlighting);
  }
}
