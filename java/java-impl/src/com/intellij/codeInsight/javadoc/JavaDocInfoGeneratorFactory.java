// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDocInfoGeneratorFactory {
  public static JavaDocInfoGeneratorFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaDocInfoGeneratorFactory.class);
  }

  protected JavaDocInfoGenerator createImpl(@NotNull Project project, @Nullable PsiElement element) {
    return new JavaDocInfoGenerator(project, element, false);
  }

  protected JavaDocInfoGenerator createImpl(@NotNull Project project, @Nullable PsiElement element, boolean isGenerationForRenderedDoc) {
    return new JavaDocInfoGenerator(project, element, isGenerationForRenderedDoc);
  }

  @NotNull
  public static JavaDocInfoGenerator create(@NotNull Project project, @Nullable PsiElement element) {
    return getInstance().createImpl(project, element, false);
  }

  @NotNull
  public static JavaDocInfoGenerator create(@NotNull Project project, @Nullable PsiElement element, boolean isGenerationForRenderedDoc) {
    return getInstance().createImpl(project, element, isGenerationForRenderedDoc);
  }
}
