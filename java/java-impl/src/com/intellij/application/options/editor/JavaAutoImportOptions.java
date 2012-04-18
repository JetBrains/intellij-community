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

/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.regex.Pattern;

public class JavaAutoImportOptions implements AutoImportOptionsProvider {
  private static final String INSERT_IMPORTS_ALWAYS = ApplicationBundle.message("combobox.insert.imports.all");
  private static final String INSERT_IMPORTS_ASK = ApplicationBundle.message("combobox.insert.imports.ask");
  private static final String INSERT_IMPORTS_NONE = ApplicationBundle.message("combobox.insert.imports.none");

  private JComboBox mySmartPasteCombo;
  private JCheckBox myCbShowImportPopup;
  private JPanel myWholePanel;
  private JCheckBox myCbAddUnambiguousImports;
  private JCheckBox myCbOptimizeImports;
  private JPanel myExcludeFromImportAndCompletionPanel;
  private JBList myExcludePackagesList;
  private DefaultListModel myExcludePackagesModel;
  @NonNls private static final Pattern ourPackagePattern = Pattern.compile("(\\w+\\.)*\\w+");

  public JavaAutoImportOptions() {
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ALWAYS);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ASK);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_NONE);

    myExcludePackagesList = new JBList();
    myExcludeFromImportAndCompletionPanel.add(
      ToolbarDecorator.createDecorator(myExcludePackagesList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            InputValidator validator = new InputValidator() {

              public boolean checkInput(String inputString) {
                return ourPackagePattern.matcher(inputString).matches();
              }

              public boolean canClose(String inputString) {
                return checkInput(inputString);
              }
            };
            String packageName = Messages.showInputDialog(myWholePanel, ApplicationBundle.message("exclude.from.completion.prompt"),
                                                          ApplicationBundle.message("exclude.from.completion.title"),
                                                          Messages.getWarningIcon(), "", validator);
            addExcludePackage(packageName);
          }
        }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

    myExcludePackagesList.getEmptyText().setText(ApplicationBundle.message("exclude.from.imports.no.exclusions"));
  }

  public void addExcludePackage(String packageName) {
    if (packageName == null) {
      return;
    }
    int index = -Arrays.binarySearch(myExcludePackagesModel.toArray(), packageName) - 1;
    if (index < 0) return;

    myExcludePackagesModel.add(index, packageName);
    myExcludePackagesList.setSelectedValue(packageName, true);
    ListScrollingUtil.ensureIndexIsVisible(myExcludePackagesList, index, 0);
    IdeFocusManager.getGlobalInstance().requestFocus(myExcludePackagesList, false);
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

    myExcludePackagesModel = new DefaultListModel();
    for (String aPackage : codeInsightSettings.EXCLUDED_PACKAGES) {
      myExcludePackagesModel.add(myExcludePackagesModel.size(), aPackage);
    }
    myExcludePackagesList.setModel(myExcludePackagesModel);
  }

  public void disposeUIResources() {

  }

  public void apply() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    codeInsightSettings.ADD_IMPORTS_ON_PASTE = getSmartPasteValue();
    codeInsightSettings.EXCLUDED_PACKAGES = getExcludedPackages();
    daemonSettings.setImportHintEnabled(myCbShowImportPopup.isSelected());
    codeInsightSettings.OPTIMIZE_IMPORTS_ON_THE_FLY = myCbOptimizeImports.isSelected();
    codeInsightSettings.ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = myCbAddUnambiguousImports.isSelected();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
        }
      }
    });
  }

  private String[] getExcludedPackages() {
    String[] excludedPackages = new String[myExcludePackagesModel.size()];
    for (int i = 0; i < myExcludePackagesModel.size(); i++) {
      excludedPackages[i] = (String)myExcludePackagesModel.elementAt(i);
    }
    Arrays.sort(excludedPackages);
    return excludedPackages;
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

    isModified |= getSmartPasteValue() != codeInsightSettings.ADD_IMPORTS_ON_PASTE;
    isModified |= !Arrays.deepEquals(getExcludedPackages(), codeInsightSettings.EXCLUDED_PACKAGES);

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
