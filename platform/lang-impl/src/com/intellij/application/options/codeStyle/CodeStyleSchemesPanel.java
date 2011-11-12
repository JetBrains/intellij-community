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

package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

public class CodeStyleSchemesPanel{
  private JButton myExportButton;
  private JComboBox myCombo;
  private JButton mySaveAsButton;
  private JButton myDeleteButton;

  private final CodeStyleSchemesModel myModel;
  private JButton myImportButton;
  private JPanel myPanel;
  private JButton myExportAsGlobalButton;
  private JButton myCopyToProjectButton;
  private JBScrollPane myJBScrollPane;
  private JPanel myGlobalPanel;
  private JPanel myProjectPanel;
  private JComboBox mySettingsType;
  private JButton myCopyFromButton;
  private PopupMenu myCopyFromMenu;
  private boolean myIsReset = false;
  private NewCodeStyleSettingsPanel mySettingsPanel;
  
  private final static String GLOBAL_SETTINGS_ITEM = "Global settings";
  private final static String PROJECT_SETTINGS_ITEM = "Per project settings";

  public Collection<CodeStyleScheme> getSchemes() {
    ArrayList<CodeStyleScheme> result = new ArrayList<CodeStyleScheme>();
    for (int i = 0; i < myCombo.getItemCount(); i++) {
      Object item = myCombo.getItemAt(i);
      if (item instanceof CodeStyleScheme) {
        result.add((CodeStyleScheme)item);
      }
    }
    return result;
  }



  public CodeStyleSchemesPanel(CodeStyleSchemesModel model) {
    myModel = model;
    
    ComboBoxModel settingsTypeModel = new DefaultComboBoxModel(new String[] {GLOBAL_SETTINGS_ITEM, PROJECT_SETTINGS_ITEM});
    mySettingsType.setModel(settingsTypeModel);
    mySettingsType.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onSettingsTypeChange();
      }
    });

    final SchemesManager<CodeStyleScheme, CodeStyleSchemeImpl> schemesManager = CodeStyleSchemesModel.getSchemesManager();
    if (schemesManager.isExportAvailable()) {
      myExportButton.setVisible(true);
      myExportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          CodeStyleScheme selected = getSelectedScheme();
          ExportSchemeAction.doExport((CodeStyleSchemeImpl)selected, schemesManager);
        }
      });
      myExportButton.setMnemonic('S');
    }
    else {
      myExportButton.setVisible(false);
    }

    if (schemesManager.isImportAvailable()) {
      myImportButton.setVisible(true);
      myImportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          SchemesToImportPopup<CodeStyleScheme, CodeStyleSchemeImpl> popup = new SchemesToImportPopup<CodeStyleScheme, CodeStyleSchemeImpl>(myPanel){
            protected void onSchemeSelected(final CodeStyleSchemeImpl scheme) {
              if (scheme != null) {
                myModel.addScheme(scheme, true);
              }

            }
          };
          popup.show(schemesManager, getSchemes());

        }
      });
    }
    else {
      myImportButton.setVisible(false);
    }

    myCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!myIsReset) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  onCombo();
                }
              });
        }
      }
    });
    mySaveAsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onSaveAs();
      }
    });
    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onDelete();
      }
    });

    myCopyToProjectButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCopyToProject();
      }
    });

    myExportAsGlobalButton.addActionListener(new ActionListener(){
      public void actionPerformed(final ActionEvent e) {
        onExportProjectScheme();
      }
    });

    myCopyFromMenu = new PopupMenu();
    myCopyFromButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showCopyFromMenu();
      }
    });
    myCopyFromButton.add(myCopyFromMenu);
    myCopyFromButton.setEnabled(false);
    
    myJBScrollPane.setBorder(null);

  }

  private void onExportProjectScheme() {
    String name = Messages.showInputDialog("Enter new scheme name:", "Copy Project Scheme to Global List", Messages.getQuestionIcon());

    if (name != null) {
      CodeStyleScheme scheme = myModel.exportProjectScheme(name);

      int switchToGlobal = Messages
        .showYesNoDialog("Project scheme was copied to global scheme list as '" + scheme.getName() + ".\n" +
                         "Switch to this created scheme?",
                         "Copy Project Scheme to Global List", Messages.getQuestionIcon());


      if (switchToGlobal == 0) {
        myModel.setUsePerProjectSettings(false);
        myModel.selectScheme(scheme, null);
      }
    }

  }

  private void onDelete() {
    myModel.removeScheme(getSelectedScheme());
  }

  private void onCopyToProject() {
    myModel.copyToProject(getSelectedScheme());

    int switchToProject = Messages
      .showYesNoDialog("Scheme '" + getSelectedScheme().getName() + "' was copied to be used as the project scheme.\n" +
                       "Switch to this created scheme?",
                       "Copy Scheme to Project", Messages.getQuestionIcon());


    if (switchToProject == 0) {
      myModel.setUsePerProjectSettings(true, true);
    }
  }

  private void onSaveAs() {
    CodeStyleScheme[] schemes = CodeStyleSchemes.getInstance().getSchemes();
    ArrayList<String> names = new ArrayList<String>();
    for (int i = 0; i < schemes.length; i++) {
      CodeStyleScheme scheme = schemes[i];
      names.add(scheme.getName());
    }
    SaveSchemeDialog saveDialog = new SaveSchemeDialog(myPanel, ApplicationBundle.message("title.save.code.style.scheme.as"), names);
    saveDialog.show();
    if (saveDialog.isOK()) {
      CodeStyleScheme selectedScheme = getSelectedScheme();
      CodeStyleScheme newScheme = myModel.createNewScheme(saveDialog.getSchemeName(),
                                                                                 selectedScheme);
      myModel.addScheme(newScheme, true);
    }
  }

  private void onCombo() {
    CodeStyleScheme selected = getSelectedScheme();
    if (selected != null) {
      myModel.selectScheme(selected, this);
      updateButtons();
    }
  }

  private void updateButtons() {
    if (!myModel.isUsePerProjectSettings()) {
      boolean deleteEnabled = false;
      boolean saveAsEnabled = true;
      CodeStyleScheme selected = getSelectedScheme();
      if (selected != null) {
        deleteEnabled = !CodeStyleSchemesModel.cannotBeDeleted(selected);
      }
      myDeleteButton.setEnabled(deleteEnabled);
      if (myExportButton != null) {
        myExportButton.setEnabled(!CodeStyleSchemesModel.cannotBeModified(selected));
      }
      mySaveAsButton.setEnabled(saveAsEnabled);
      myCopyToProjectButton.setEnabled(true);
      myExportAsGlobalButton.setEnabled(false);
    }
    else {
      mySaveAsButton.setEnabled(false);
      myDeleteButton.setEnabled(false);
      myExportButton.setEnabled(false);
      myImportButton.setEnabled(false);
      myCopyToProjectButton.setEnabled(false);
      myExportAsGlobalButton.setEnabled(true);
    }
  }

  private CodeStyleScheme getSelectedScheme() {
    Object selected = myCombo.getSelectedItem();
    if (selected instanceof CodeStyleScheme) {
      return (CodeStyleScheme)selected;
    }
    return null;
  }


  public void disposeUIResources() {
    myPanel.removeAll();
  }

  public void resetSchemesCombo() {
    myIsReset = true;
    try {
      Vector schemesVector = new Vector();
      schemesVector.addAll(myModel.getSchemes());
      DefaultComboBoxModel model = new DefaultComboBoxModel(schemesVector);
      myCombo.setModel(model);
      myCombo.setSelectedItem(myModel.getSelectedGlobalScheme());

      updateProjectSchemesRelatedUI();
    }
    finally {
      myIsReset = false;
    }


  }

  private void updateProjectSchemesRelatedUI() {
    boolean useProjectSettings = myModel.isUsePerProjectSettings();
    mySettingsType.setSelectedItem(useProjectSettings ? PROJECT_SETTINGS_ITEM : GLOBAL_SETTINGS_ITEM);
    myGlobalPanel.setVisible(!useProjectSettings);
    myProjectPanel.setVisible(useProjectSettings);
    updateButtons();
  }

  public void onSelectedSchemeChanged() {
    myIsReset = true;
    try {
      myCombo.setSelectedItem(myModel.getSelectedGlobalScheme());
    }
    finally {
      myIsReset = false;
    }

    updateButtons();
  }

  public void usePerProjectSettingsOptionChanged() {
    updateProjectSchemesRelatedUI();
  }

  public JComponent getPanel() {
    return myPanel;
  }

  public void setCodeStyleSettingsPanel(NewCodeStyleSettingsPanel settingsPanel) {
    mySettingsPanel = settingsPanel;
    CodeStyleAbstractPanel selectedPanel = mySettingsPanel.getSelectedPanel();
    if (selectedPanel != null) {
      selectedPanel.setupCopyFromMenu(myCopyFromMenu);
    }
    myCopyFromButton.setEnabled(myCopyFromMenu.getItemCount() > 0);
  }

  private void onSettingsTypeChange() {
    Object selectedItem = mySettingsType.getSelectedItem();
    boolean useProjectSettings = PROJECT_SETTINGS_ITEM.equals(selectedItem);
    myGlobalPanel.setVisible(!useProjectSettings);
    myProjectPanel.setVisible(useProjectSettings);
    myModel.setUsePerProjectSettings(useProjectSettings);
  }
  
  private void showCopyFromMenu() {
    if (myCopyFromMenu.getItemCount() > 0) {
      myCopyFromMenu.show(myCopyFromButton, 0, 0);
    }
  }

}
