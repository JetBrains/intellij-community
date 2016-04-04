/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.*;
import java.awt.*;

public class JavaAutoImportOptions implements AutoImportOptionsProvider {
  private static final String INSERT_IMPORTS_ALWAYS = ApplicationBundle.message("combobox.insert.imports.all");
  private static final String INSERT_IMPORTS_ASK = ApplicationBundle.message("combobox.insert.imports.ask");
  private static final String INSERT_IMPORTS_NONE = ApplicationBundle.message("combobox.insert.imports.none");

  private JComboBox mySmartPasteCombo;
  private JCheckBox myCbShowImportPopup;
  private JPanel myWholePanel;
  private JCheckBox myCbAddUnambiguousImports;
  private JCheckBox myCbAddMethodImports;
  private JCheckBox myCbOptimizeImports;
  private JPanel myExcludeFromImportAndCompletionPanel;
  private final ExcludeTable myExcludePackagesTable;

  public JavaAutoImportOptions(Project project) {
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ALWAYS);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ASK);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_NONE);

    myExcludePackagesTable = new ExcludeTable(project);
    myExcludeFromImportAndCompletionPanel.add(myExcludePackagesTable.getComponent(), BorderLayout.CENTER);
  }

  public void addExcludePackage(String packageName) {
    myExcludePackagesTable.addExcludePackage(packageName);
  }

  public void reset() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    switch (codeInsightSettings.ADD_IMPORTS_ON_PASTE) {
      case CodeInsightSettings.YES:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_ALWAYS);
        break;

      case CodeInsightSettings.NO:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_NONE);
        break;

      case CodeInsightSettings.ASK:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_ASK);
        break;
    }


    myCbShowImportPopup.setSelected(daemonSettings.isImportHintEnabled());
    myCbOptimizeImports.setSelected(codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY);
    myCbAddUnambiguousImports.setSelected(codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    myCbAddMethodImports.setSelected(codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

    myExcludePackagesTable.reset();
  }

  public void disposeUIResources() {

  }

  public void apply() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    codeInsightSettings.ADD_IMPORTS_ON_PASTE = getSmartPasteValue();
    daemonSettings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY = myCbOptimizeImports.isSelected();
    codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = myCbAddUnambiguousImports.isSelected();
    codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY = myCbAddMethodImports.isSelected();

    myExcludePackagesTable.apply();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
  }

  public JComponent createComponent() {
    return myWholePanel;
  }

  public boolean isModified() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    boolean isModified = isModified(myCbShowImportPopup, daemonSettings.isImportHintEnabled());
    isModified |= isModified(myCbOptimizeImports, codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY);
    isModified |= isModified(myCbAddUnambiguousImports, codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY);
    isModified |= isModified(myCbAddMethodImports, codeInsightSettings.ADD_MEMBER_IMPORTS_ON_THE_FLY);

    isModified |= getSmartPasteValue() != codeInsightSettings.ADD_IMPORTS_ON_PASTE;
    isModified |= myExcludePackagesTable.isModified();

    return isModified;
  }

  private int getSmartPasteValue() {
    Object selectedItem = mySmartPasteCombo.getSelectedItem();
    if (INSERT_IMPORTS_ALWAYS.equals(selectedItem)) {
      return CodeInsightSettings.YES;
    }
    else if (INSERT_IMPORTS_NONE.equals(selectedItem)) {
      return CodeInsightSettings.NO;
    }
    else {
      return CodeInsightSettings.ASK;
    }
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }
}
