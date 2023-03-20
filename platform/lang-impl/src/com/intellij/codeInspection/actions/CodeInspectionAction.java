// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.application.options.schemes.SchemesCombo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.InjectableLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.header.InspectionProfileSchemesModel;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CodeInspectionAction extends BaseAnalysisAction {
  private static final Logger LOG = Logger.getInstance(CodeInspectionAction.class);
  private static final String LAST_SELECTED_PROFILE_PROP = "run.code.analysis.last.selected.profile";

  private int myRunId;
  private GlobalInspectionContextImpl myGlobalInspectionContext;
  protected InspectionProfileImpl myExternalProfile;

  public CodeInspectionAction() {
    super(InspectionsBundle.messagePointer("inspection.action.title"), InspectionsBundle.messagePointer("inspection.action.noun"));
  }

  public CodeInspectionAction(@NlsContexts.DialogTitle String title, @Nls String analysisNoon) {
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

  protected void runInspections(@NotNull Project project,
                                @NotNull AnalysisScope scope) {
    int runId = ++myRunId;
    scope.setSearchInLibraries(false);
    FileDocumentManager.getInstance().saveAllDocuments();

    InspectionProfileImpl externalProfile = myExternalProfile;
    final GlobalInspectionContextImpl inspectionContext = getGlobalInspectionContext(project);
    inspectionContext.setRerunAction(() -> DumbService.getInstance(project).smartInvokeLater(() -> {
      //someone called the runInspections before us, we cannot restore the state
      if (runId != myRunId) return;
      if (project.isDisposed()) return;
      if (!scope.isValid()) return;

      //restore current state
      myExternalProfile = externalProfile;
      myGlobalInspectionContext = inspectionContext;

      FileDocumentManager.getInstance().saveAllDocuments();
      analyze(project, scope);
    }));

    inspectionContext.setExternalProfile(externalProfile);
    inspectionContext.setCurrentScope(scope);
    inspectionContext.doInspections(scope);
  }


  private GlobalInspectionContextImpl getGlobalInspectionContext(Project project) {
    if (myGlobalInspectionContext == null) {
      myGlobalInspectionContext = ((InspectionManagerEx)InspectionManager.getInstance(project)).createNewGlobalContext();
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
  protected JComponent getAdditionalActionSettings(@NotNull final Project project, final @NotNull BaseAnalysisActionDialog dialog) {
    dialog.setShowInspectInjectedCode(true);
    final CodeInspectionAdditionalUi ui = new CodeInspectionAdditionalUi();
    final InspectionManagerEx manager = (InspectionManagerEx)InspectionManager.getInstance(project);
    final SchemesCombo<InspectionProfileImpl> profiles = ui.getBrowseProfilesCombo();
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    final ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(project);
    ui.getLink().addActionListener(__ -> {
      final ExternalProfilesComboboxAwareInspectionToolsConfigurable errorConfigurable = createConfigurable(projectProfileManager, profiles);
      final MySingleConfigurableEditor editor = new MySingleConfigurableEditor(project, errorConfigurable, manager);
      if (editor.showAndGet()) {
        reloadProfiles(profiles, profileManager, projectProfileManager, project);
        if (errorConfigurable.mySelectedName != null) {
          final InspectionProfileImpl profile = (errorConfigurable.mySelectedIsProjectProfile ? projectProfileManager : profileManager)
            .getProfile(errorConfigurable.mySelectedName);
          profiles.selectScheme(profile);
        }
      }
      else {
        //if profile was disabled and cancel after apply was pressed
        final InspectionProfile profile = profiles.getSelectedScheme();
        final boolean canExecute = profile != null && profile.isExecutable(project);
        dialog.setOKActionEnabled(canExecute);
      }
    });
    profiles.addActionListener(__ -> {
      myExternalProfile = profiles.getSelectedScheme();
      final boolean canExecute = myExternalProfile != null && myExternalProfile.isExecutable(project);
      dialog.setOKActionEnabled(canExecute);
      if (canExecute) {
        PropertiesComponent.getInstance(project).setValue(LAST_SELECTED_PROFILE_PROP, (myExternalProfile.isProjectLevel() ? 'p' : 'a') + myExternalProfile.getName());
        manager.setProfile(myExternalProfile.getName());
      }
    });
    reloadProfiles(profiles, profileManager, projectProfileManager, project);
    if (hasEnabledInspectionsOnInjectableCode(project)) {
      dialog.setAnalyzeInjectedCode(false);
    }
    return ui.getPanel();
  }

  private boolean hasEnabledInspectionsOnInjectableCode(Project project) {
    if (myExternalProfile != null) {
      return ContainerUtil.exists(myExternalProfile.getAllEnabledInspectionTools(project),
                                  tool -> Language.findLanguageByID(tool.getTool().getLanguage()) instanceof InjectableLanguage);
    }
    return false;
  }
  
  protected ExternalProfilesComboboxAwareInspectionToolsConfigurable createConfigurable(ProjectInspectionProfileManager projectProfileManager,
                                                                                        SchemesCombo<InspectionProfileImpl> profilesCombo) {
    return new ExternalProfilesComboboxAwareInspectionToolsConfigurable(projectProfileManager, profilesCombo);
  }

  protected static class ExternalProfilesComboboxAwareInspectionToolsConfigurable extends InspectionToolsConfigurable {
    private final SchemesCombo<InspectionProfileImpl> myProfilesCombo;
    private String mySelectedName;
    private boolean mySelectedIsProjectProfile;

    public ExternalProfilesComboboxAwareInspectionToolsConfigurable(@NotNull ProjectInspectionProfileManager projectProfileManager, SchemesCombo<InspectionProfileImpl> profilesCombo) {
      super(projectProfileManager);
      myProfilesCombo = profilesCombo;
    }

    @Override
    protected InspectionProfileImpl getCurrentProfile() {
      return myProfilesCombo.getSelectedScheme();
    }

    @Override
    protected void applyRootProfile(@NotNull String name, boolean isProjectLevel) {
      mySelectedName = name;
      mySelectedIsProjectProfile = isProjectLevel;
    }
  }

  private void reloadProfiles(@NotNull SchemesCombo<InspectionProfileImpl> profilesCombo,
                              InspectionProfileManager appProfileManager,
                              InspectionProjectProfileManager projectProfileManager,
                              Project project) {
    profilesCombo.resetSchemes(InspectionProfileSchemesModel.getSortedProfiles(appProfileManager, projectProfileManager));
    InspectionProfileImpl selectedProfile = getProfileToUse(project, appProfileManager, projectProfileManager);
    profilesCombo.selectScheme(selectedProfile);
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
        LOG.assertTrue(type == 'p', "Unexpected last selected profile: '" + lastSelectedProfile + "'");
        final InspectionProfileImpl profile = projectProfileManager.getProfile(lastSelectedProfileName, false);
        if (profile != null && profile.isProjectLevel()) return profile;
      }
    }
    return getGlobalInspectionContext(project).getCurrentProfile();
  }

  private static class MySingleConfigurableEditor extends SingleConfigurableEditor {
    private final InspectionManagerEx myManager;

    MySingleConfigurableEditor(final Project project, final ErrorsConfigurable configurable, InspectionManagerEx manager) {
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
