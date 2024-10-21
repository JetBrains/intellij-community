// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.find.FindUsagesSettings;
import com.intellij.ide.util.scopeChooser.ScopeIdMapper;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.StateRestoringCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.*;

public abstract class JavaFindUsagesDialog<T extends JavaFindUsagesOptions> extends CommonFindUsagesDialog {
  protected StateRestoringCheckBox myCbIncludeOverloadedMethods;
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
    myIncludeOverloadedMethodsAvailable = isIncludeOverloadedMethodsAvailable();
    super.init();
  }

  public boolean isIncludeOverloadedMethodsAvailable() {
    return myPsiElement instanceof PsiMethod && MethodSignatureUtil.hasOverloads((PsiMethod)myPsiElement);
  }

  public void calcFindUsagesOptions(T options) {
    if (options instanceof JavaMethodFindUsagesOptions) {
      ((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages =
        myIncludeOverloadedMethodsAvailable && isToChange(myCbIncludeOverloadedMethods) && myCbIncludeOverloadedMethods.isSelected();
    }
  }

  protected List<EventPair<?>> createFeatureUsageData(T options) {
    List<EventPair<?>> data = new ArrayList<>();
    data.add(USAGES.with(options.isUsages));
    data.add(TEXT_OCCURRENCES.with(options.isSearchForTextOccurrences));

    String serializedName = ScopeIdMapper.getInstance().getScopeSerializationId(options.searchScope.getDisplayName());
    if (ScopeIdMapper.getStandardNames().contains(serializedName)) {
      data.add(SEARCH_SCOPE.with(serializedName));
    }

    return data;
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
        FindUsagesSettings.getInstance().setSearchOverloadedMethods(myCbIncludeOverloadedMethods.isSelected());
      }
    }
    else {
      return;
    }
    super.doOKAction();
  }

  @Override
  protected void addUsagesOptions(@NotNull JPanel optionsPanel) {
    if (myIncludeOverloadedMethodsAvailable) {
      myCbIncludeOverloadedMethods = addCheckboxToPanel(JavaBundle.message("find.options.include.overloaded.methods.checkbox"),
                                                        FindUsagesSettings.getInstance().isSearchOverloadedMethods(), optionsPanel, false);

    }
    addDefaultOptions(optionsPanel);
  }

  protected void addDefaultOptions(@NotNull JPanel optionsPanel) {
    super.addUsagesOptions(optionsPanel);
  }

  protected final @NotNull PsiElement getPsiElement() {
    return myPsiElement;
  }

  protected @NotNull T getFindUsagesOptions() {
    return (T)myFindUsagesOptions;
  }
}
