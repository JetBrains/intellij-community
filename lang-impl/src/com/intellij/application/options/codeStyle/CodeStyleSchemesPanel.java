package com.intellij.application.options.codeStyle;

import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

public class CodeStyleSchemesPanel{
  private JButton myExportButton;
  private JComboBox myCombo;
  private JButton mySaveAsButton;
  private JButton myDeleteButton;

  private final CodeStyleSchemesModel myModel;
  private JRadioButton myUseGlobalScheme;
  private JRadioButton myUseProjectScheme;
  private JButton myImportButton;
  private JPanel myPanel;

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


    myUseGlobalScheme.addItemListener(new ItemListener(){
      public void itemStateChanged(final ItemEvent e) {
        myModel.setUsePerProjectSettings(!myUseGlobalScheme.isSelected());
      }
    });

    myUseProjectScheme.addItemListener(new ItemListener(){
      public void itemStateChanged(final ItemEvent e) {
        myModel.setUsePerProjectSettings(myUseProjectScheme.isSelected());
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
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                onCombo();
              }
            });
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


  }

  private void onDelete() {
    myModel.removeScheme(getSelectedScheme());
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
      CodeStyleScheme newScheme = CodeStyleSchemes.getInstance().createNewScheme(saveDialog.getSchemeName(),
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
    }
    else {
      mySaveAsButton.setEnabled(false);
      myDeleteButton.setEnabled(false);
      myExportButton.setEnabled(false);
      myImportButton.setEnabled(false);
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
    Vector schemesVector = new Vector();
    schemesVector.addAll(myModel.getSchemes());
    DefaultComboBoxModel model = new DefaultComboBoxModel(schemesVector);
    myCombo.setModel(model);

    updateProjectSchemesRelatedUI();


  }

  private void updateProjectSchemesRelatedUI() {
    if(myModel.isUsePerProjectSettings()) {
      myUseProjectScheme.setSelected(true);
      myCombo.setEnabled(false);
      updateButtons();
    }
    else if (!myModel.isUsePerProjectSettings()){
      myUseGlobalScheme.setSelected(true);
      myCombo.setEnabled(true);
      updateButtons();
    }
  }

  public void onSelectedSchemeChanged() {
    myCombo.setSelectedItem(myModel.getSelectedGlobalScheme());
    updateButtons();
  }

  public void usePerProjectSettingsOptionChanged() {
    updateProjectSchemesRelatedUI();
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
