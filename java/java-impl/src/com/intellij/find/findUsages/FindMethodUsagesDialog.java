// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ImplicitToStringSearch;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FindMethodUsagesDialog extends JavaFindUsagesDialog<JavaMethodFindUsagesOptions> {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbImplementingMethods;
  private StateRestoringCheckBox myCbOverridingMethods;
  private StateRestoringCheckBox myCbImplicitToString;
  private boolean myHasFindWhatPanel;

  public FindMethodUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean mustOpenInNewTab,
                                boolean isSingleFile,
                                FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? myCbUsages : null;
  }

  @Override
  public void calcFindUsagesOptions(JavaMethodFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    if (isToChange(myCbOverridingMethods)) {
      options.isOverridingMethods = isSelected(myCbOverridingMethods);
    }
    if (isToChange(myCbImplementingMethods)) {
      options.isImplementingMethods = isSelected(myCbImplementingMethods);
    }
    if (isToChange(myCbImplicitToString)) {
      options.isImplicitToString = isSelected(myCbImplicitToString);
    }
    options.isCheckDeepInheritance = true;
    FUCounterUsageLogger.getInstance().logEvent(myPsiElement.getProject(), EVENT_LOG_GROUP, "find.method.started", createFeatureUsageData(options));
  }

  @Override
  protected FeatureUsageData createFeatureUsageData(JavaMethodFindUsagesOptions options) {
    FeatureUsageData data = super.createFeatureUsageData(options);
    data.addData("overridingMethods", options.isOverridingMethods);
    data.addData("implementingMethods", options.isImplementingMethods);
    data.addData("includeInherited", options.isIncludeInherited);
    data.addData("includeOverload", options.isIncludeOverloadUsages);
    data.addData("implicitCalls", options.isImplicitToString);
    return data;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaBundle.message("find.what.group")));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(JavaBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiMethod method = (PsiMethod) getPsiElement();
    PsiClass aClass = method.getContainingClass();
    if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        aClass == null ||
        aClass instanceof PsiAnonymousClass ||
        aClass.hasModifierProperty(PsiModifier.FINAL)) {
      myHasFindWhatPanel = false;
      return null;
    }

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      myCbImplementingMethods =
        addCheckboxToPanel(JavaBundle.message("find.what.implementing.methods.checkbox"), getFindUsagesOptions().isImplementingMethods,
                           findWhatPanel, true);
    }
    else {
      myCbOverridingMethods =
        addCheckboxToPanel(JavaBundle.message("find.what.overriding.methods.checkbox"), getFindUsagesOptions().isOverridingMethods,
                           findWhatPanel, true);
    }
    if (ImplicitToStringSearch.isToStringMethod(method)) {
      myCbImplicitToString =
        addCheckboxToPanel(JavaBundle.message("find.what.implicit.to.string.checkbox"), getFindUsagesOptions().isImplicitToString,
                           findWhatPanel, true);
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

  @Override
  protected void update() {
    if (!myHasFindWhatPanel) {
      setOKActionEnabled(true);
    } else {
      boolean hasSelected = isSelected(myCbUsages) ||
                            isSelected(myCbImplementingMethods) ||
                            isSelected(myCbOverridingMethods) ||
                            isSelected(myCbImplicitToString);
      setOKActionEnabled(hasSelected);
    }
  }
}