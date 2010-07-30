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
  private final FindUsagesOptions myFindClassOptions;
  private final FindUsagesOptions myFindMethodOptions;
  private final FindUsagesOptions myFindPackageOptions;
  private final FindUsagesOptions myFindThrowOptions;                   
  private final FindUsagesOptions myFindVariableOptions;

  public static JavaFindUsagesHandlerFactory getInstance(@NotNull Project project) {
    return ContainerUtil.findInstance(Extensions.getExtensions(EP_NAME, project), JavaFindUsagesHandlerFactory.class);
  }

  public JavaFindUsagesHandlerFactory(Project project) {
    final JavaFindUsagesOptions findClassOptions = JavaFindUsagesHandler.createFindUsagesOptions(project);
    final JavaFindUsagesOptions findMethodOptions = JavaFindUsagesHandler.createFindUsagesOptions(project);
    findMethodOptions.isCheckDeepInheritance = false;
    findMethodOptions.isIncludeSubpackages = false;
    findMethodOptions.isSearchForTextOccurrences = false;
    final FindUsagesOptions findPackageOptions = JavaFindUsagesHandler.createFindUsagesOptions(project);

    final JavaFindUsagesOptions findThrowOptions = JavaFindUsagesHandler.createFindUsagesOptions(project);
    findThrowOptions.isSearchForTextOccurrences = false;
    findThrowOptions.isThrowUsages = true;

    final JavaFindUsagesOptions findVariableOptions = JavaFindUsagesHandler.createFindUsagesOptions(project);
    findVariableOptions.isCheckDeepInheritance = false;
    findVariableOptions.isIncludeSubpackages = false;
    findVariableOptions.isSearchForTextOccurrences = false;

    myFindClassOptions = findClassOptions;
    myFindMethodOptions = findMethodOptions;
    myFindPackageOptions = findPackageOptions;
    myFindThrowOptions = findThrowOptions;
    myFindVariableOptions = findVariableOptions;
  }

  public boolean canFindUsages(@NotNull final PsiElement element) {
    return new JavaFindUsagesProvider().canFindUsagesFor(element);
  }

  public FindUsagesHandler createFindUsagesHandler(@NotNull final PsiElement element, final boolean forHighlightUsages) {
    if (element instanceof PsiDirectory) {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage == null
             ? null
             : new JavaFindUsagesHandler(psiPackage, myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                         myFindVariableOptions);
    }

    if (element instanceof PsiMethod && !forHighlightUsages) {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods((PsiMethod)element, JavaFindUsagesHandler.ACTION_STRING);
      if (methods.length > 1) {
        return new JavaFindUsagesHandler(element, methods, myFindClassOptions, myFindMethodOptions, myFindPackageOptions,
                                         myFindThrowOptions, myFindVariableOptions);
      }
      if (methods.length == 1) {
        return new JavaFindUsagesHandler(methods[0], myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                         myFindVariableOptions);
      }
      return FindUsagesHandler.NULL_HANDLER;
    }

    return new JavaFindUsagesHandler(element, myFindClassOptions, myFindMethodOptions, myFindPackageOptions, myFindThrowOptions,
                                     myFindVariableOptions);
  }

  public FindUsagesOptions getFindClassOptions() {
    return myFindClassOptions;
  }

  public FindUsagesOptions getFindMethodOptions() {
    return myFindMethodOptions;
  }

  public FindUsagesOptions getFindPackageOptions() {
    return myFindPackageOptions;
  }

  public FindUsagesOptions getFindThrowOptions() {
    return myFindThrowOptions;
  }

  public FindUsagesOptions getFindVariableOptions() {
    return myFindVariableOptions;
  }
}
