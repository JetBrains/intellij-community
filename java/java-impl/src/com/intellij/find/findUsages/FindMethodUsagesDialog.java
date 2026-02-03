// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.ImplicitToStringSearch;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.util.List;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.FIND_METHOD_STARTED;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.IMPLEMENTING_METHODS;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.IMPLICIT_CALLS;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.INCLUDE_INHERITED;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.INCLUDE_OVERLOAD;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.OVERRIDING_METHODS;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.SEARCH_FOR_BASE_METHODS;

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
  public @Nullable JComponent getPreferredFocusedControl() {
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

    PsiElement method = getPsiElement();
    if (noSpecificOptions(method)) {
      myHasFindWhatPanel = false;
      return null;
    }

    if (canSearchForBaseMethod(method)) {
      myCbSearchForBase = createCheckbox(JavaBundle.message("find.what.search.for.base.methods.checkbox"),
                                         getFindUsagesOptions().isSearchForBaseMethod, true);

      JComponent decoratedCheckbox = new ComponentPanelBuilder(myCbSearchForBase).
        withComment(JavaBundle.message("find.what.search.for.base.methods.checkbox.comment")).createPanel();
      decoratedCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
      findWhatPanel.add(decoratedCheckbox);
    }

    if (canSearchForImplementingMethods(method)) {
      myCbImplementingMethods = addCheckboxToPanel(JavaBundle.message("find.what.implementing.methods.checkbox"),
                                                    getFindUsagesOptions().isImplementingMethods, findWhatPanel, true);
    }
    else if (canSearchForOverridingMethods(method)) {
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

  protected boolean noSpecificOptions(PsiElement element) {
    return !(element instanceof PsiMethod method) || method.getContainingClass() == null;
  }

  protected boolean canSearchForOverridingMethods(PsiElement element) {
    if (!(element instanceof PsiMethod method)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    return !(aClass instanceof PsiAnonymousClass) &&
           !aClass.hasModifierProperty(PsiModifier.FINAL) &&
           !method.isConstructor() &&
           !method.hasModifierProperty(PsiModifier.FINAL) &&
           !method.hasModifierProperty(PsiModifier.STATIC) &&
           !method.hasModifierProperty(PsiModifier.PRIVATE);
  }

  protected boolean canSearchForImplementingMethods(PsiElement element) {
    return element instanceof PsiMethod method && method.hasModifierProperty(PsiModifier.ABSTRACT);
  }

  protected boolean canSearchForBaseMethod(PsiElement element) {
    return element instanceof PsiMethod method &&
           !method.hasModifierProperty(PsiModifier.STATIC) &&
           !method.hasModifierProperty(PsiModifier.PRIVATE) &&
           !method.isConstructor();
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