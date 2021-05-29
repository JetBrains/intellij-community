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

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;

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

    FUCounterUsageLogger.getInstance().logEvent(myPsiElement.getProject(), EVENT_LOG_GROUP, "find.variable.started", createFeatureUsageData(options));
  }

  @Override
  protected FeatureUsageData createFeatureUsageData(JavaVariableFindUsagesOptions options) {
    FeatureUsageData data = super.createFeatureUsageData(options);
    data.addData("searchForBaseAccessors", options.isSearchForBaseAccessors);
    data.addData("searchForAccessors", options.isSearchForAccessors);
    data.addData("searchInOverriding", options.isSearchInOverridingMethods);
    data.addData("readAccess", options.isReadAccess);
    data.addData("writeAccess", options.isWriteAccess);
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