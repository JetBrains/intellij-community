package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.ui.ErrorOptionsConfigurable;
import com.intellij.ui.ComboboxWithBrowseButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class CodeInspectionAction extends BaseAnalysisAction {
  private GlobalInspectionContextImpl myGlobalInspectionContext = null;

  public CodeInspectionAction() {
    super(InspectionsBundle.message("inspection.action.title"), InspectionsBundle.message("inspection.action.noun"));
  }

  protected String getHelpTopic() {
    return "runningInspections";
  }

  protected void analyze(Project project, AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final InspectionManagerEx inspectionManagerEx = ((InspectionManagerEx)InspectionManager.getInstance(project));
    final GlobalInspectionContextImpl inspectionContext = getGlobalInspectionContext(project);
    inspectionContext.setCurrentScope(scope);
    inspectionContext.doInspections(scope, inspectionManagerEx);
    myGlobalInspectionContext = null;
  }


  private GlobalInspectionContextImpl getGlobalInspectionContext(Project project) {
    if (myGlobalInspectionContext == null) {
      myGlobalInspectionContext = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).createNewGlobalContext(false);
    }
    return myGlobalInspectionContext;
  }


  protected void canceled() {
    super.canceled();
    myGlobalInspectionContext = null;
  }

  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    final AdditionalPanel panel = new AdditionalPanel();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    final JComboBox profiles = panel.myBrowseProfilesCombo.getComboBox();
    profiles.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Profile profile = (Profile)value;
        setText(profile.getName());
        setIcon(profile.isLocal() ? Profile.LOCAL_PROFILE : Profile.PROJECT_PROFILE);
        return rendererComponent;
      }
    });
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    reloadProfiles(profiles, profileManager, projectProfileManager, manager);
    panel.myBrowseProfilesCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ErrorOptionsConfigurable errorConfigurable = ErrorOptionsConfigurable.getInstance(project);
        final MySingleConfigurableEditor editor = new MySingleConfigurableEditor(project, errorConfigurable, manager);
        errorConfigurable.selectNodeInTree(((Profile)profiles.getSelectedItem()).getName());
        editor.show();
        if (editor.isOK()) {
          reloadProfiles(profiles, profileManager, projectProfileManager, manager);
        }
        else {
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
        if (canExecute) {
          manager.setProfile(profile.getName());
        }
      }
    });
    final InspectionProfile profile = (InspectionProfile)profiles.getSelectedItem();
    dialog.setOKActionEnabled(profile != null && profile.isExecutable());

    panel.myRunWithChoosenButton.setSelected(!getGlobalInspectionContext(project).RUN_WITH_EDITOR_PROFILE);
    panel.myRunWithEditorSettingsButton.setSelected(getGlobalInspectionContext(project).RUN_WITH_EDITOR_PROFILE);
    panel.myBrowseProfilesCombo.setEnabled(!getGlobalInspectionContext(project).RUN_WITH_EDITOR_PROFILE);

    final ActionListener onChoose = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        getGlobalInspectionContext(project).RUN_WITH_EDITOR_PROFILE = panel.myRunWithEditorSettingsButton.isSelected();
        panel.myBrowseProfilesCombo.setEnabled(!getGlobalInspectionContext(project).RUN_WITH_EDITOR_PROFILE);
      }
    };
    panel.myRunWithEditorSettingsButton.addActionListener(onChoose);
    panel.myRunWithChoosenButton.addActionListener(onChoose);

    return panel.myAdditionalPanel;
  }

  private void reloadProfiles(JComboBox profiles,
                              InspectionProfileManager inspectionProfileManager,
                              InspectionProjectProfileManager inspectionProjectProfileManager,
                              InspectionManagerEx inspectionManager) {
    final InspectionProfile selectedProfile = getGlobalInspectionContext(inspectionManager.getProject()).getCurrentProfile();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)profiles.getModel();
    model.removeAllElements();
    fillModel(inspectionProfileManager, model);
    fillModel(inspectionProjectProfileManager, model);
    profiles.setSelectedItem(selectedProfile);
  }

  private static void fillModel(final ProfileManager inspectionProfileManager, final DefaultComboBoxModel model) {
    Collection<Profile> profiles = inspectionProfileManager.getProfiles().values();
    for (Profile profile : profiles) {
      model.addElement(profile);
    }
  }


  private static class AdditionalPanel {
    public JRadioButton myRunWithEditorSettingsButton;
    public JRadioButton myRunWithChoosenButton;
    public ComboboxWithBrowseButton myBrowseProfilesCombo;
    public JPanel myAdditionalPanel;
  }

  private static class MySingleConfigurableEditor extends SingleConfigurableEditor {
    private InspectionManagerEx myManager;

    public MySingleConfigurableEditor(final Project project, final ErrorOptionsConfigurable configurable, InspectionManagerEx manager) {
      super(project, configurable, createDimensionKey(configurable));
      myManager = manager;
    }


    protected void doOKAction() {
      final Object o = ((ErrorOptionsConfigurable)getConfigurable()).getSelectedObject();
      if (o instanceof Profile) {
        myManager.setProfile(((Profile)o).getName());
      }
      super.doOKAction();
    }
  }
}
