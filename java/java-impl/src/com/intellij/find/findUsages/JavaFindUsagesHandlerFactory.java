// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class JavaFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  private final JavaClassFindUsagesOptions myFindClassOptions;
  private final JavaMethodFindUsagesOptions myFindMethodOptions;
  private final JavaPackageFindUsagesOptions myFindPackageOptions;
  private final JavaThrowFindUsagesOptions myFindThrowOptions;
  private final JavaVariableFindUsagesOptions myFindVariableOptions;

  public static JavaFindUsagesHandlerFactory getInstance(@NotNull Project project) {
    return ContainerUtil.findInstance(EP_NAME.getExtensions(project), JavaFindUsagesHandlerFactory.class);
  }

  public JavaFindUsagesHandlerFactory(Project project) {
    myFindClassOptions = new JavaClassFindUsagesOptions(project);
    myFindMethodOptions = new JavaMethodFindUsagesOptions(project);
    myFindPackageOptions = new JavaPackageFindUsagesOptions(project);
    myFindThrowOptions = new JavaThrowFindUsagesOptions(project);
    myFindVariableOptions = new JavaVariableFindUsagesOptions(project);
  }

  @Override
  public boolean canFindUsages(@NotNull final PsiElement element) {
    return new JavaFindUsagesProvider().canFindUsagesFor(element);
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element,  @NotNull OperationMode operationMode) {
    if (element instanceof PsiDirectory) {
      PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage == null ? null : new JavaFindUsagesHandler(psiPackage, this);
    }

    return new JavaFindUsagesHandler(element, this);
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull final PsiElement element, boolean forHighlightUsages) {
    return createFindUsagesHandler(element, forHighlightUsages ? OperationMode.HIGHLIGHT_USAGES : OperationMode.DEFAULT);
  }

  @NotNull
  public JavaClassFindUsagesOptions getFindClassOptions() {
    return myFindClassOptions;
  }

  @NotNull
  public JavaMethodFindUsagesOptions getFindMethodOptions() {
    return myFindMethodOptions;
  }

  @NotNull
  public JavaPackageFindUsagesOptions getFindPackageOptions() {
    return myFindPackageOptions;
  }

  @NotNull
  public JavaThrowFindUsagesOptions getFindThrowOptions() {
    return myFindThrowOptions;
  }

  @NotNull
  public JavaVariableFindUsagesOptions getFindVariableOptions() {
    return myFindVariableOptions;
  }
}
