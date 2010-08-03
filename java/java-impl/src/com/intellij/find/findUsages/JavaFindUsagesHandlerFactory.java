/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.find.findUsages;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.lang.java.JavaFindUsagesProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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
    return ContainerUtil.findInstance(Extensions.getExtensions(EP_NAME, project), JavaFindUsagesHandlerFactory.class);
  }

  public JavaFindUsagesHandlerFactory(Project project) {
    myFindClassOptions = new JavaClassFindUsagesOptions(project);
    myFindMethodOptions = new JavaMethodFindUsagesOptions(project);
    myFindPackageOptions = new JavaPackageFindUsagesOptions(project);
    myFindThrowOptions = new JavaThrowFindUsagesOptions(project);
    myFindVariableOptions = new JavaVariableFindUsagesOptions(project);
  }

  public boolean canFindUsages(@NotNull final PsiElement element) {
    return new JavaFindUsagesProvider().canFindUsagesFor(element);
  }

  public FindUsagesHandler createFindUsagesHandler(@NotNull final PsiElement element, final boolean forHighlightUsages) {
    if (element instanceof PsiDirectory) {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage == null ? null : new JavaFindUsagesHandler(psiPackage, this);
    }

    if (element instanceof PsiMethod && !forHighlightUsages) {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, JavaFindUsagesHandler.ACTION_STRING);
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

  public JavaClassFindUsagesOptions getFindClassOptions() {
    return myFindClassOptions;
  }

  public JavaMethodFindUsagesOptions getFindMethodOptions() {
    return myFindMethodOptions;
  }

  public JavaPackageFindUsagesOptions getFindPackageOptions() {
    return myFindPackageOptions;
  }

  public JavaThrowFindUsagesOptions getFindThrowOptions() {
    return myFindThrowOptions;
  }

  public JavaVariableFindUsagesOptions getFindVariableOptions() {
    return myFindVariableOptions;
  }
}
