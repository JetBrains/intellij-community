// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaSpecialRefactoringProviderImpl implements JavaSpecialRefactoringProvider {

  @Override
  public void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination) throws IncorrectOperationException {
    MoveClassesOrPackagesUtil.moveDirectoryRecursively(dir, destination);
  }

  @Override
  public LightMethodObjectExtractedData extractLightMethodObject(Project project,
                                                                 @Nullable PsiElement originalContext,
                                                                 @NotNull PsiCodeFragment fragment,
                                                                 @NotNull String methodName,
                                                                 @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException {
    return ExtractLightMethodObjectHandler.extractLightMethodObject(project, originalContext, fragment, methodName, javaVersion);
  }
}
