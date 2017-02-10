/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.profile.codeInspection.ui.header.ProfilesComboBox;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CodeInspectionAction extends BaseAnalysisAction {
  private static final Logger LOG = Logger.getInstance(CodeInspectionAction.class);
  private static final String LAST_SELECTED_PROFILE_PROP = "run.code.analysis.last.selected.profile";

  private GlobalInspectionContextImpl myGlobalInspectionContext;
  protected InspectionProfileImpl myExternalProfile;

  public CodeInspectionAction() {
    super(InspectionsBundle.message("inspection.action.title"), InspectionsBundle.message("inspection.action.noun"));
  }

  public CodeInspectionAction(String title, String analysisNoon) {
    super(title, analysisNoon);
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    try {
      runInspections(project, scope);
    }
    finally {
      myGlobalInspectionContext = null;
      myExternalProfile = null;
    }
  }

  protected void runInspections(Project project, AnalysisScope scope) {
    scope.setSearchInLibraries(false);
    FileDocumentManager.getInstance().saveAllDocuments();
    final GlobalInspectionContextImpl inspectionContext = getGlobalInspectionContext(project);
    inspectionContext.setExternalProfile(myExternalProfile);
    inspectionContext.setCurrentScope(scope);
    inspectionContext.doInspections(scope);
  }


  private GlobalInspectionContextImpl getGlobalInspectionContext(Project project) {
    if (myGlobalInspectionContext == null) {
      myGlobalInspectionContext = ((InspectionManagerEx)InspectionManager.getInstance(project)).createNewGlobalContext(false);
    }
    return myGlobalInspectionContext;
  }

  @Override
  @NonNls
  protected String getHelpTopic() {
    return "reference.dialogs.inspection.scope";
  }

  @Override
  protected void canceled() {
    super.canceled();
    myGlobalInspectionContext = null;
  }

  @Override
  protected JComponent getAdditionalActionSettings(@NotNull final Project project, final BaseAnalysisActionDialog dialog) {
    final AdditionalPanel panel = new AdditionalPanel();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    final ProfilesComboBox<InspectionProfileImpl> profiles = (ProfilesComboBox<InspectionProfileImpl>)panel.myBrowseProfilesCombo.getComboBox();
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    final ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(project);
    panel.myBrowseProfilesCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final InspectionToolsConfigurable errorConfigurable = createConfigurable(projectProfileManager, profileManager, profiles);
        final MySingleConfigurableEditor editor = new MySingleConfigurableEditor(project, errorConfigurable, manager);
        if (editor.showAndGet()) {
          reloadProfiles(profiles, profileManager, projectProfileManager, manager);
        }
        else {
          //if profile was disabled and cancel after apply was pressed
          final InspectionProfile profile = (InspectionProfile)profiles.getSelectedItem();
          final boolean canExecute = profile != null && profile.isExecutable(project);
          dialog.setOKActionEnabled(canExecute);
        }
      }
    });
    profiles.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myExternalProfile = (InspectionProfileImpl)profiles.getSelectedItem();
        final boolean canExecute = myExternalProfile != null && myExternalProfile.isExecutable(project);
        dialog.setOKActionEnabled(canExecute);
        if (canExecute) {
          PropertiesComponent.getInstance(project).setValue(LAST_SELECTED_PROFILE_PROP, (myExternalProfile.isProjectLevel() ? 'p' : 'a') + myExternalProfile.getName());
          manager.setProfile(myExternalProfile.getName());
        }
      }
    });
    reloadProfiles(profiles, profileManager, projectProfileManager, manager);
    return panel.myAdditionalPanel;
  }

  protected InspectionToolsConfigurable createConfigurable(ProjectInspectionProfileManager projectProfileManager,
                                                           InspectionProfileManager profileManager,
                                                           final ProfilesComboBox profilesCombo) {
    return new ExternalProfilesComboboxAwareInspectionToolsConfigurable(projectProfileManager, profilesCombo);
  }

  protected static class ExternalProfilesComboboxAwareInspectionToolsConfigurable extends InspectionToolsConfigurable {
    private final ProfilesComboBox<InspectionProfileImpl> myProfilesCombo;

    public ExternalProfilesComboboxAwareInspectionToolsConfigurable(@NotNull ProjectInspectionProfileManager projectProfileManager, ProfilesComboBox profilesCombo) {
      super(projectProfileManager);
      myProfilesCombo = profilesCombo;
    }

    @Override
    protected InspectionProfileImpl getCurrentProfile() {
      return (InspectionProfileImpl)myProfilesCombo.getSelectedItem();
    }

    @Override
    protected void addProfile(InspectionProfileModifiableModel model) {
      super.addProfile(model);
      myProfilesCombo.addProfile(model.getSource());
    }

    @Override
    protected void applyRootProfile(@NotNull String name, boolean isProjectLevel) {
      for (int i = 0; i < myProfilesCombo.getItemCount(); i++) {
        final InspectionProfileImpl profile = myProfilesCombo.getItemAt(i);
        if (name.equals(profile.getName())) {
          myProfilesCombo.setSelectedIndex(i);
          break;
        }
      }
    }
  }


  private void reloadProfiles(ProfilesComboBox<InspectionProfileImpl> profilesCombo,
                              InspectionProfileManager inspectionProfileManager,
                              InspectionProjectProfileManager inspectionProjectProfileManager,
                              InspectionManagerEx inspectionManager) {
    InspectionProfileImpl selectedProfile = getProfileToUse(inspectionManager.getProject(), inspectionProfileManager, inspectionProjectProfileManager);
    List<InspectionProfileImpl> profiles = new ArrayList<>();
    profiles.addAll(inspectionProfileManager.getProfiles());
    profiles.addAll(inspectionProjectProfileManager.getProfiles());
    profilesCombo.reset(profiles);
    profilesCombo.selectProfile(selectedProfile);
  }

  @NotNull
  private InspectionProfileImpl getProfileToUse(@NotNull Project project,
                                               @NotNull InspectionProfileManager appProfileManager,
                                               @NotNull InspectionProjectProfileManager projectProfileManager) {
    final String lastSelectedProfile = PropertiesComponent.getInstance(project).getValue(LAST_SELECTED_PROFILE_PROP);
    if (lastSelectedProfile != null) {
      final char type = lastSelectedProfile.charAt(0);
      final String lastSelectedProfileName = lastSelectedProfile.substring(1);
      if (type == 'a') {
        final InspectionProfileImpl profile = appProfileManager.getProfile(lastSelectedProfileName, false);
        if (profile != null) return profile;
      } else {
        LOG.assertTrue(type == 'p', "Unexpected last selected profile: \'" + lastSelectedProfile + "\'");
        final InspectionProfileImpl profile = projectProfileManager.getProfile(lastSelectedProfileName, false);
        if (profile != null && profile.isProjectLevel()) return profile;
      }
    }
    return getGlobalInspectionContext(project).getCurrentProfile();
  }

  private static class AdditionalPanel {
    public ComboboxWithBrowseButton myBrowseProfilesCombo;
    public JPanel myAdditionalPanel;

    private void createUIComponents() {
      myBrowseProfilesCombo = new ComboboxWithBrowseButton(new ProfilesComboBox() {
        @Override
        protected void onProfileChosen(InspectionProfileImpl inspectionProfile) {
          //do nothing here
        }
      });
    }
  }

  private static class MySingleConfigurableEditor extends SingleConfigurableEditor {
    private final InspectionManagerEx myManager;

    public MySingleConfigurableEditor(final Project project, final ErrorsConfigurable configurable, InspectionManagerEx manager) {
      super(project, configurable, createDimensionKey(configurable));
      myManager = manager;
    }


    @Override
    protected void doOKAction() {
      final Object o = ((ErrorsConfigurable)getConfigurable()).getSelectedObject();
      if (o instanceof InspectionProfile) {
        myManager.setProfile(((InspectionProfile)o).getName());
      }
      super.doOKAction();
    }
  }
}
