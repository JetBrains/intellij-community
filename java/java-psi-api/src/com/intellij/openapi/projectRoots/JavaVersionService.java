// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.projectRoots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaVersionService {
  public static JavaVersionService getInstance() {
    return ApplicationManager.getApplication().getService(JavaVersionService.class);
  }

  public boolean isAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion version) {
    return PsiUtil.getLanguageLevel(element).isAtLeast(version.getMaxLanguageLevel());
  }

  public @Nullable JavaSdkVersion getJavaSdkVersion(@NotNull PsiElement element) {
    return JavaSdkVersion.fromLanguageLevel(PsiUtil.getLanguageLevel(element));
  }

  public boolean isCompilerVersionAtLeast(@NotNull PsiElement element, @NotNull JavaSdkVersion version) {
    return isAtLeast(element, version);
  }
}