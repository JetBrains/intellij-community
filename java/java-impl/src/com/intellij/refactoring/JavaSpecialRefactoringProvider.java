// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * WARNING! Not a real extension point, used to work around module dependencies. It is an implementation detail, may be changed without warning.
 *
 * Provides handlers used for tests and specific scenarios, as well as different utility functions.
 */
@ApiStatus.Internal
public interface JavaSpecialRefactoringProvider {
  static JavaSpecialRefactoringProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaSpecialRefactoringProvider.class);
  }

  // null means unchanged

  // null means unchanged

  LightMethodObjectExtractedData extractLightMethodObject(final Project project,
                                                          @Nullable PsiElement originalContext,
                                                          @NotNull final PsiCodeFragment fragment,
                                                          @NotNull String methodName,
                                                          @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException;

  // utils which have too many deps

  void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination) throws IncorrectOperationException;
}
