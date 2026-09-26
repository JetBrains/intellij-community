// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.definition;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public abstract class AbstractBasicJavaDefinitionService {

  public static final ExtensionPointName<AbstractBasicJavaDefinitionService>
    EP_NAME = ExtensionPointName.create("com.intellij.java.definitions");

  private static class AbstractBasicJavaDefinitionServiceHelper {
    private static final AbstractBasicJavaDefinitionService INSTANCE = EP_NAME.getExtensionList().get(0);
  }

  public static AbstractBasicJavaDefinitionService getJavaDefinitionService() {
    return AbstractBasicJavaDefinitionServiceHelper.INSTANCE;
  }

  public abstract @NotNull LanguageLevel getLanguageLevel(@NotNull PsiElement psiElement);

  public abstract @NotNull LanguageLevel getLanguageLevel(@NotNull Project project);
}
