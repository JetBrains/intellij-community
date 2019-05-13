/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class JavaFindUsagesDialog<T extends JavaFindUsagesOptions> extends CommonFindUsagesDialog {
  private StateRestoringCheckBox myCbIncludeOverloadedMethods;
  private boolean myIncludeOverloadedMethodsAvailable;

  protected JavaFindUsagesDialog(@NotNull PsiElement element,
                                 @NotNull Project project,
                                 @NotNull FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab,
                                 boolean mustOpenInNewTab,
                                 boolean isSingleFile,
                                 @NotNull FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  protected void init() {
    myIncludeOverloadedMethodsAvailable = myPsiElement instanceof PsiMethod && MethodSignatureUtil.hasOverloads((PsiMethod)myPsiElement);
    super.init();
  }

  public void calcFindUsagesOptions(T options) {
    if (options instanceof JavaMethodFindUsagesOptions) {
      ((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages =
        myIncludeOverloadedMethodsAvailable && isToChange(myCbIncludeOverloadedMethods) && myCbIncludeOverloadedMethods.isSelected();
    }
  }

  @Override
  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);
    calcFindUsagesOptions((T)options);
  }

  @Override
  protected void doOKAction() {
    if (shouldDoOkAction()) {
      if (myIncludeOverloadedMethodsAvailable) {
        FindSettings.getInstance().setSearchOverloadedMethods(myCbIncludeOverloadedMethods.isSelected());
      }
    }
    else {
      return;
    }
    super.doOKAction();
  }

  @Override
  protected void addUsagesOptions(JPanel optionsPanel) {
    super.addUsagesOptions(optionsPanel);
    if (myIncludeOverloadedMethodsAvailable) {
      myCbIncludeOverloadedMethods = addCheckboxToPanel(FindBundle.message("find.options.include.overloaded.methods.checkbox"),
                                                        FindSettings.getInstance().isSearchOverloadedMethods(), optionsPanel, false);

    }
  }

  @NotNull
  protected final PsiElement getPsiElement() {
    return myPsiElement;
  }

  @NotNull
  protected T getFindUsagesOptions() {
    return (T)myFindUsagesOptions;
  }
}
