// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.*;

public class FindVariableUsagesDialog extends JavaFindUsagesDialog<JavaVariableFindUsagesOptions> {
  private StateRestoringCheckBox myCbSearchForAccessors;
  private StateRestoringCheckBox myCbSearchForBase;
  private StateRestoringCheckBox myCbSearchInOverridingMethods;

  public FindVariableUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions,
                                  boolean toShowInNewTab, boolean mustOpenInNewTab, boolean isSingleFile,
                                  FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  public JComponent getPreferredFocusedControl() {
    return myCbToSkipResultsWhenOneUsage;
  }

  @Override
  public void calcFindUsagesOptions(JavaVariableFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isReadAccess = true;
    options.isWriteAccess = true;

    if (isToChange(myCbSearchForAccessors)) {
      options.isSearchForAccessors = isSelected(myCbSearchForAccessors);
    }
    if (isToChange(myCbSearchInOverridingMethods)) {
      options.isSearchInOverridingMethods = isSelected(myCbSearchInOverridingMethods);
    }
    if (isToChange(myCbSearchForBase)) {
      options.isSearchForBaseAccessors = isSelected(myCbSearchForBase);
    }

    FIND_VARIABLE_STARTED.log(myPsiElement.getProject(), createFeatureUsageData(options));
  }

  @Override
  protected List<EventPair<?>> createFeatureUsageData(JavaVariableFindUsagesOptions options) {
    List<EventPair<?>> data = super.createFeatureUsageData(options);
    data.add(SEARCH_FOR_BASE_ACCESSOR.with(options.isSearchForBaseAccessors));
    data.add(SEARCH_FOR_ACCESSORS.with(options.isSearchForAccessors));
    data.add(SEARCH_IN_OVERRIDING.with(options.isSearchInOverridingMethods));
    data.add(READ_ACCESS.with(options.isReadAccess));
    data.add(WRITE_ACCESS.with(options.isWriteAccess));
    return data;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    PsiElement element = getPsiElement();
    if (element instanceof PsiField) {
      myCbSearchForAccessors = addCheckboxToPanel(JavaBundle.message("find.options.include.accessors.checkbox"),
                                                  getFindUsagesOptions().isSearchForAccessors, findWhatPanel, true);

      PsiField field = (PsiField)element;
      JavaFindUsagesHandler handler = (JavaFindUsagesHandler)myUsagesHandler;
      if (!handler.getFieldAccessors(field).isEmpty()) {
        myCbSearchForBase = createCheckbox(JavaBundle.message("find.options.include.accessors.base.checkbox"),
                                               getFindUsagesOptions().isSearchForBaseAccessors, true);
        JComponent decoratedCheckbox = new ComponentPanelBuilder(myCbSearchForBase).
          withComment(JavaBundle.message("find.options.include.accessors.base.checkbox.comment")).createPanel();
        decoratedCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        findWhatPanel.add(decoratedCheckbox);
      }
    }

    if (element instanceof PsiParameter) {
      myCbSearchInOverridingMethods = addCheckboxToPanel(JavaBundle.message("find.options.search.overriding.methods.checkbox"),
                                                         getFindUsagesOptions().isSearchInOverridingMethods, findWhatPanel, true);
    }

    return findWhatPanel;
  }

  @Override
  protected void update() {
    setOKActionEnabled(true);
  }
}