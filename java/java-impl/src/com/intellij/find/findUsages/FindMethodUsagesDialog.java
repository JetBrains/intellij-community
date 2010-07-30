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

import com.intellij.find.FindBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FindMethodUsagesDialog extends JavaFindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbImplementingMethods;
  private StateRestoringCheckBox myCbOverridingMethods;
  private boolean myHasFindWhatPanel;

  public FindMethodUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean mustOpenInNewTab,
                                boolean isSingleFile,
                                FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Nullable
  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? myCbUsages : null;
  }

  public void calcFindUsagesOptions(JavaFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    if (isToChange(myCbOverridingMethods)) {
      options.isOverridingMethods = isSelected(myCbOverridingMethods);
    }
    if (isToChange(myCbImplementingMethods)) {
      options.isImplementingMethods = isSelected(myCbImplementingMethods);
    }
    options.isCheckDeepInheritance = true;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiMethod method = (PsiMethod) getPsiElement();
    PsiClass aClass = method.getContainingClass();
    if (!method.isConstructor() &&
            !method.hasModifierProperty(PsiModifier.STATIC) &&
            !method.hasModifierProperty(PsiModifier.FINAL) &&
            !method.hasModifierProperty(PsiModifier.PRIVATE) &&
            aClass != null &&
            !(aClass instanceof PsiAnonymousClass) &&
            !aClass.hasModifierProperty(PsiModifier.FINAL)) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        myCbImplementingMethods = addCheckboxToPanel(FindBundle.message("find.what.implementing.methods.checkbox"), getFindUsagesOptions().isImplementingMethods, findWhatPanel, true);
      } else {
        myCbOverridingMethods = addCheckboxToPanel(FindBundle.message("find.what.overriding.methods.checkbox"), getFindUsagesOptions().isOverridingMethods, findWhatPanel, true);
      }
    } else {
      myHasFindWhatPanel = false;
      return null; 
    }
    myHasFindWhatPanel = true;
    return findWhatPanel;

    /*if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        aClass == null ||
        aClass instanceof PsiAnonymousClass ||
        aClass.hasModifierProperty(PsiModifier.FINAL)){
      myHasFindWhatPanel = false;
      return null;
    }
    else{
      myHasFindWhatPanel = true;
      return findWhatPanel;
    }*/
  }

  protected void update() {
    if (!myHasFindWhatPanel) {
      setOKActionEnabled(true);
    } else {

      boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbImplementingMethods) || isSelected(myCbOverridingMethods);
      setOKActionEnabled(hasSelected);
    }
  }
}