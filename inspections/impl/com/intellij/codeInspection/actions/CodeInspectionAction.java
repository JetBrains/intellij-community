package com.intellij.codeInspection.actions;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ui.InspectCodePanel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.ui.ComboboxWithBrowseButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CodeInspectionAction extends BaseAnalysisAction {
  private JRadioButton myRunWithEditorSettingsButton;
  private JRadioButton myRunWithChoosenButton;
  private ComboboxWithBrowseButton myBrowseProfilesCombo;
  private JPanel myAdditionalPanel;

  public CodeInspectionAction() {
    super(InspectionsBundle.message("inspection.action.title"), InspectionsBundle.message("inspection.action.noun"));
  }

  protected void analyze(Project project, AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = ((InspectionManagerEx)InspectionManager.getInstance(project));
    inspectionManagerEx.setCurrentScope(scope);
    inspectionManagerEx.doInspections(scope);
  }

  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    final JComboBox profiles = myBrowseProfilesCombo.getComboBox();
    profiles.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected,
                                                                               cellHasFocus);
        final Profile profile = (Profile)value;
        setText(profile.getName());
        setIcon(profile.isLocal() ? Profile.LOCAL_PROFILE : Profile.PROJECT_PROFILE);
        return rendererComponent;
      }
    });
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    reloadProfiles(profiles, profileManager, projectProfileManager, manager);
    myBrowseProfilesCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        InspectCodePanel inspectCodeDialog = new InspectCodePanel(manager, null, ((Profile)profiles.getSelectedItem()).getName(), true, projectProfileManager){
          protected void init() {
            super.init();
            setOKButtonText(CommonBundle.getOkButtonText());
          }
        };
        inspectCodeDialog.show();
        if (inspectCodeDialog.isOK()){
          reloadProfiles(profiles, profileManager, projectProfileManager, manager);
        } else {
          //if profile was disabled and cancel after apply was pressed
          final InspectionProfile profile = (InspectionProfile)profiles.getSelectedItem();
          final boolean canExecute = profile != null && profile.isExecutable();
          dialog.setOKActionEnabled(canExecute);
        }
      }
    });
    profiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfile profile = (InspectionProfile)profiles.getSelectedItem();
        final boolean canExecute = profile != null && profile.isExecutable();
        dialog.setOKActionEnabled(canExecute);
        if (canExecute){
          manager.setProfile(profile.getName());
        }
      }
    });
    final InspectionProfile profile = (InspectionProfile)profiles.getSelectedItem();
    dialog.setOKActionEnabled(profile != null && profile.isExecutable());

    myRunWithChoosenButton.setSelected(!manager.RUN_WITH_EDITOR_PROFILE);
    myRunWithEditorSettingsButton.setSelected(manager.RUN_WITH_EDITOR_PROFILE);
    myBrowseProfilesCombo.setEnabled(!manager.RUN_WITH_EDITOR_PROFILE);

    final ActionListener onChoose = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        manager.RUN_WITH_EDITOR_PROFILE = myRunWithEditorSettingsButton.isSelected();
        myBrowseProfilesCombo.setEnabled(!manager.RUN_WITH_EDITOR_PROFILE);
      }
    };
    myRunWithEditorSettingsButton.addActionListener(onChoose);
    myRunWithChoosenButton.addActionListener(onChoose);

    return myAdditionalPanel;
  }

  private static void reloadProfiles(JComboBox profiles, InspectionProfileManager inspectionProfileManager, InspectionProjectProfileManager inspectionProjectProfileManager, InspectionManagerEx inspectionManager){
    final InspectionProfile selectedProfile = inspectionManager.getCurrentProfile();
    String[] avaliableProfileNames = inspectionProfileManager.getAvailableProfileNames();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)profiles.getModel();
    model.removeAllElements();
    for (String profile : avaliableProfileNames) {
      model.addElement(inspectionProfileManager.getProfile(profile));
    }
    if (inspectionProjectProfileManager.useProjectLevelProfileSettings()){
      avaliableProfileNames = inspectionProjectProfileManager.getAvailableProfileNames();
      for (String profile : avaliableProfileNames) {
        model.addElement(inspectionProjectProfileManager.getProfile(profile));
      }
    }
    profiles.setSelectedItem(selectedProfile);
  }
}
