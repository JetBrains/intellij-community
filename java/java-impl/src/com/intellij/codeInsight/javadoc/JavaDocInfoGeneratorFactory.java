// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class JavaDocInfoGeneratorFactory {
  public static JavaDocInfoGeneratorFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaDocInfoGeneratorFactory.class);
  }

  protected JavaDocInfoGenerator createImpl(Project project, PsiElement element) {
    return new JavaDocInfoGenerator(project, element);
  }

  @NotNull
  public static JavaDocInfoGenerator create(Project project, PsiElement element) {
    return getInstance().createImpl(project, element);
  }
}
