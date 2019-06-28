// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author peter
*/
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
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage == null ? null : new JavaFindUsagesHandler(psiPackage, this);
    }

    if (element instanceof PsiMethod && operationMode != OperationMode.HIGHLIGHT_USAGES) {
      PsiMethod method = (PsiMethod)element;
      final PsiMethod[] methods;

      if (operationMode == OperationMode.USAGES_WITH_DEFAULT_OPTIONS && 
          Registry.is("java.find.usages.always.use.top.hierarchy.methods")) {
        methods = SuperMethodWarningUtil.getTargetMethodCandidates(method, Collections.emptyList());
      }
      else {
        methods = SuperMethodWarningUtil.checkSuperMethods(method, JavaFindUsagesHandler.ACTION_STRING);
      }

      if (methods.length > 1) {
        return new JavaFindUsagesHandler(element, methods, this);
      }
      if (methods.length == 1) {
        return new JavaFindUsagesHandler(methods[0], this);
      }
      return FindUsagesHandler.NULL_HANDLER;
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
