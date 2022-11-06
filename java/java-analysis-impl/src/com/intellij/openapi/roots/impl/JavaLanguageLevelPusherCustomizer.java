// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// you don't need it
// introduced to remove separated GWT language level pusher
@ApiStatus.Internal
public interface JavaLanguageLevelPusherCustomizer {
  ExtensionPointName<JavaLanguageLevelPusherCustomizer> EP_NAME = ExtensionPointName.create("com.intellij.javaLanguageLevelPusherCustomizer");

  @Nullable LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file);

  @Nullable
  @NlsContexts.DetailedDescription String getInconsistencyLanguageLevelMessage(@NotNull String message,
                                                                                      @NotNull LanguageLevel level,
                                                                                      @NotNull PsiFile file);

  static @Nullable LanguageLevel getImmediateValueImpl(@NotNull Project project, @Nullable VirtualFile file) {
    for (JavaLanguageLevelPusherCustomizer customizer : EP_NAME.getExtensionList()) {
      LanguageLevel level = customizer.getImmediateValue(project, file);
      if (level != null) return level;
    }
    return null;
  }

  @Nullable
  static @NlsContexts.DetailedDescription String getInconsistencyLanguageLevelMessageImpl(@NotNull String message,
                                                                                          @NotNull LanguageLevel level,
                                                                                          @NotNull PsiFile file) {
    for (JavaLanguageLevelPusherCustomizer customizer : EP_NAME.getExtensionList()) {
      String customizedMsg = customizer.getInconsistencyLanguageLevelMessage(message, level, file);
      if (customizedMsg != null) return null;
    }
    return null;
  }
}
