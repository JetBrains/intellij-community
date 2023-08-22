// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages;

import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.util.List;

import static com.intellij.find.findUsages.JavaFindUsagesCollector.CLASSES_USAGES;
import static com.intellij.find.findUsages.JavaFindUsagesCollector.FIND_PACKAGE_STARTED;

public class FindPackageUsagesDialog extends JavaFindUsagesDialog<JavaPackageFindUsagesOptions> {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbClassesUsages;

  public FindPackageUsagesDialog(PsiElement element,
                                 Project project,
                                 FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab, boolean mustOpenInNewTab,
                                 boolean isSingleFile, FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  @Override
  public void calcFindUsagesOptions(JavaPackageFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages);
    if (isToChange(myCbClassesUsages)){
      options.isClassesUsages = isSelected(myCbClassesUsages);
    }
    options.isSkipPackageStatements = false;
    options.isSkipImportStatements = false;
    FIND_PACKAGE_STARTED.log(myPsiElement.getProject(), createFeatureUsageData(options));
  }

  @Override
  protected List<EventPair<?>> createFeatureUsageData(JavaPackageFindUsagesOptions options) {
    List<EventPair<?>> data = super.createFeatureUsageData(options);
    data.add(CLASSES_USAGES.with(options.isClassesUsages));
    return data;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(JavaBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);
    myCbClassesUsages = addCheckboxToPanel(JavaBundle.message("find.what.usages.of.classes.and.interfaces"), getFindUsagesOptions().isClassesUsages, findWhatPanel, true);

    return findWhatPanel;
  }

  @Override
  protected void update() {
    if(myCbToSearchForTextOccurrences != null){
      if (isSelected(myCbUsages)){
        myCbToSearchForTextOccurrences.makeSelectable();
      }
      else{
        myCbToSearchForTextOccurrences.makeUnselectable(false);
      }
    }

    boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbClassesUsages);
    setOKActionEnabled(hasSelected);
  }
}