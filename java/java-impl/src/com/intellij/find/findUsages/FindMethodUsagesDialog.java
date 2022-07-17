// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ImplicitToStringSearch;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.*;

public class FindMethodUsagesDialog extends JavaFindUsagesDialog<JavaMethodFindUsagesOptions> {
  private StateRestoringCheckBox myCbSearchForBase;
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbImplementingMethods;
  private StateRestoringCheckBox myCbOverridingMethods;
  private StateRestoringCheckBox myCbImplicitToString;
  private boolean myHasFindWhatPanel;

  public FindMethodUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions,
                                boolean toShowInNewTab, boolean mustOpenInNewTab, boolean isSingleFile, FindUsagesHandler handler) {
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
    if (isToChange(myCbSearchForBase)) {
      options.isSearchForBaseMethod = isSelected(myCbSearchForBase);
    }
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
    FIND_METHOD_STARTED.log(myPsiElement.getProject(), createFeatureUsageData(options));

  }

  @Override
  protected List<EventPair<?>> createFeatureUsageData(JavaMethodFindUsagesOptions options) {
    List<EventPair<?>> data = super.createFeatureUsageData(options);
    data.add(SEARCH_FOR_BASE_METHODS.with(options.isSearchForBaseMethod));
    data.add(OVERRIDING_METHODS.with(options.isOverridingMethods));
    data.add(IMPLEMENTING_METHODS.with(options.isImplementingMethods));
    data.add(INCLUDE_INHERITED.with(options.isIncludeInherited));
    data.add(INCLUDE_OVERLOAD.with(options.isIncludeOverloadUsages));
    data.add(IMPLICIT_CALLS.with(options.isImplicitToString));
    return data;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(JavaBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiMethod method = (PsiMethod) getPsiElement();
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      myHasFindWhatPanel = false;
      return null;
    }

    if (!method.hasModifierProperty(PsiModifier.STATIC) &&
        !method.hasModifierProperty(PsiModifier.PRIVATE) &&
        !method.isConstructor()) {
      myCbSearchForBase = createCheckbox(JavaBundle.message("find.what.search.for.base.methods.checkbox"),
                                         getFindUsagesOptions().isSearchForBaseMethod, true);

      JComponent decoratedCheckbox = new ComponentPanelBuilder(myCbSearchForBase).
        withComment(JavaBundle.message("find.what.search.for.base.methods.checkbox.comment")).createPanel();
      decoratedCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
      findWhatPanel.add(decoratedCheckbox);
    }

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      myCbImplementingMethods = addCheckboxToPanel(JavaBundle.message("find.what.implementing.methods.checkbox"),
                                                    getFindUsagesOptions().isImplementingMethods, findWhatPanel, true);
    }
    else if (!(aClass instanceof PsiAnonymousClass) &&
             !aClass.hasModifierProperty(PsiModifier.FINAL) &&
             !method.isConstructor() &&
             !method.hasModifierProperty(PsiModifier.FINAL) &&
             !method.hasModifierProperty(PsiModifier.STATIC) &&
             !method.hasModifierProperty(PsiModifier.PRIVATE)) {
      myCbOverridingMethods = addCheckboxToPanel(JavaBundle.message("find.what.overriding.methods.checkbox"),
                                                 getFindUsagesOptions().isOverridingMethods, findWhatPanel, true);
    }
    if (ImplicitToStringSearch.isToStringMethod(method)) {
      myCbImplicitToString = addCheckboxToPanel(JavaBundle.message("find.what.implicit.to.string.checkbox"),
                                                getFindUsagesOptions().isImplicitToString, findWhatPanel, true);
    }

    myHasFindWhatPanel = true;
    return findWhatPanel;
  }

  @Override
  protected void update() {
    if (!myHasFindWhatPanel) {
      setOKActionEnabled(true);
    } else {
      boolean hasSelected = isSelected(myCbUsages) ||
                            isSelected(myCbSearchForBase) ||
                            isSelected(myCbImplementingMethods) ||
                            isSelected(myCbOverridingMethods) ||
                            isSelected(myCbImplicitToString);
      setOKActionEnabled(hasSelected);
    }
  }
}