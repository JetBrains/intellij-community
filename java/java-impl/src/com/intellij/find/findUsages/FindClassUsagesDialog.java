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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;

public class FindClassUsagesDialog extends JavaFindUsagesDialog {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbMethodsUsages;
  private StateRestoringCheckBox myCbFieldsUsages;
  private StateRestoringCheckBox myCbImplementingClasses;
  private StateRestoringCheckBox myCbDerivedInterfaces;
  private StateRestoringCheckBox myCbDerivedClasses;

  public FindClassUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean mustOpenInNewTab,
                               boolean isSingleFile,
                               FindUsagesHandler handler){
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  public void calcFindUsagesOptions(JavaFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    if (isToChange(myCbUsages)){
      options.isUsages = isSelected(myCbUsages);
    }
    if (isToChange(myCbMethodsUsages)){
      options.isMethodsUsages = isSelected(myCbMethodsUsages);
    }
    if (isToChange(myCbFieldsUsages)){
      options.isFieldsUsages = isSelected(myCbFieldsUsages);
    }
    if (isToChange(myCbDerivedClasses)){
      options.isDerivedClasses = isSelected(myCbDerivedClasses);
    }
    if (isToChange(myCbImplementingClasses)){
      options.isImplementingClasses = isSelected(myCbImplementingClasses);
    }
    if (isToChange(myCbDerivedInterfaces)){
      options.isDerivedInterfaces = isSelected(myCbDerivedInterfaces);
    }
    options.isSkipImportStatements = false;
    options.isCheckDeepInheritance = true;
    options.isIncludeInherited = false;
  }

  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();

    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiClass psiClass = (PsiClass)getPsiElement();
    myCbMethodsUsages = addCheckboxToPanel(FindBundle.message("find.what.methods.usages.checkbox"), getFindUsagesOptions().isMethodsUsages, findWhatPanel, true);

    if (!psiClass.isAnnotationType()) {
      myCbFieldsUsages = addCheckboxToPanel(FindBundle.message("find.what.fields.usages.checkbox"), getFindUsagesOptions().isFieldsUsages, findWhatPanel, true);
      if (psiClass.isInterface()){
        myCbImplementingClasses = addCheckboxToPanel(FindBundle.message("find.what.implementing.classes.checkbox"), getFindUsagesOptions().isImplementingClasses, findWhatPanel, true);
        myCbDerivedInterfaces = addCheckboxToPanel(FindBundle.message("find.what.derived.interfaces.checkbox"), getFindUsagesOptions().isDerivedInterfaces, findWhatPanel, true);
      }
      else if (!psiClass.hasModifierProperty(PsiModifier.FINAL)){
        myCbDerivedClasses = addCheckboxToPanel(FindBundle.message("find.what.derived.classes.checkbox"), getFindUsagesOptions().isDerivedClasses, findWhatPanel, true);
      }
    }
    return findWhatPanel;
  }

  protected void update() {
    if(myCbToSearchForTextOccurences != null){
      if (isSelected(myCbUsages)){
        myCbToSearchForTextOccurences.makeSelectable();
      }
      else{
        myCbToSearchForTextOccurences.makeUnselectable(false);
      }
    }

    boolean hasSelected = isSelected(myCbUsages) ||
      isSelected(myCbFieldsUsages) ||
      isSelected(myCbMethodsUsages) ||
      isSelected(myCbImplementingClasses) ||
      isSelected(myCbDerivedInterfaces) ||
      isSelected(myCbDerivedClasses);
    setOKActionEnabled(hasSelected);
  }

}