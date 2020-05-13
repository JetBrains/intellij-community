// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class JavaAutoImportOptions implements AutoImportOptionsProvider {
  private JComboBox<String> mySmartPasteCombo;
  private JCheckBox myCbShowImportPopup;
  private JPanel myWholePanel;
  private JCheckBox myCbAddUnambiguousImports;
  private JCheckBox myCbAddMethodImports;
  private JCheckBox myCbOptimizeImports;
  private JPanel myExcludeFromImportAndCompletionPanel;
  private final ExcludeTable myExcludePackagesTable;
  private final Project myProject;

  public JavaAutoImportOptions(@NotNull Project project) {
    myProject = project;

    mySmartPasteCombo.addItem(getInsertImportsAlways());
    mySmartPasteCombo.addItem(getInsertImportsAsk());
    mySmartPasteCombo.addItem(getInsertImportsNone());

    myExcludePackagesTable = new ExcludeTable(project);
    myExcludeFromImportAndCompletionPanel.add(myExcludePackagesTable.getComponent(), BorderLayout.CENTER);
  }

  public void addExcludePackage(@NotNull String packageName) {
    myExcludePackagesTable.addExcludePackage(packageName);
  }

  @Override
  public void reset() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    switch (codeInsightSettings.ADD_IMPORTS_ON_PASTE) {
      case CodeInsightSettings.YES:
        mySmartPasteCombo.setSelectedItem(getInsertImportsAlways());
        break;

      case CodeInsightSettings.NO:
        mySmartPasteCombo.setSelectedItem(getInsertImportsNone());
        break;

      case CodeInsightSettings.ASK:
        mySmartPasteCombo.setSelectedItem(getInsertImportsAsk());
        break;
    }


    myCbShowImportPopup.setSelected(daemonSettings.isImportHintEnabled());
    myCbOptimizeImports.setSelected(CodeInsightWorkspaceSettings.getInstance(myProject).optimizeImportsOnTheFly);
    myCbAddUnambiguousImports.setSelected(codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    myCbAddMethodImports.setSelected(codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

    myExcludePackagesTable.reset();
  }

  @Override
  public void apply() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    codeInsightSettings.ADD_IMPORTS_ON_PASTE = getSmartPasteValue();
    daemonSettings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    CodeInsightWorkspaceSettings.getInstance(myProject).optimizeImportsOnTheFly = myCbOptimizeImports.isSelected();
    codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = myCbAddUnambiguousImports.isSelected();
    codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY = myCbAddMethodImports.isSelected();

    myExcludePackagesTable.apply();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }

  @Override
  public JComponent createComponent() {
    return myWholePanel;
  }

  @Override
  public boolean isModified() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    boolean isModified = isModified(myCbShowImportPopup, daemonSettings.isImportHintEnabled());
    isModified |= isModified(myCbOptimizeImports, CodeInsightWorkspaceSettings.getInstance(myProject).optimizeImportsOnTheFly);
    isModified |= isModified(myCbAddUnambiguousImports, codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    isModified |= isModified(myCbAddMethodImports, codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

    isModified |= getSmartPasteValue() != codeInsightSettings.ADD_IMPORTS_ON_PASTE;
    isModified |= myExcludePackagesTable.isModified();

    return isModified;
  }

  private int getSmartPasteValue() {
    Object selectedItem = mySmartPasteCombo.getSelectedItem();
    if (getInsertImportsAlways().equals(selectedItem)) {
      return CodeInsightSettings.YES;
    }
    else if (getInsertImportsNone().equals(selectedItem)) {
      return CodeInsightSettings.NO;
    }
    else {
      return CodeInsightSettings.ASK;
    }
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static String getInsertImportsAlways() {
    return ApplicationBundle.message("combobox.insert.imports.all");
  }

  private static String getInsertImportsAsk() {
    return ApplicationBundle.message("combobox.insert.imports.ask");
  }

  private static String getInsertImportsNone() {
    return ApplicationBundle.message("combobox.insert.imports.none");
  }
}
