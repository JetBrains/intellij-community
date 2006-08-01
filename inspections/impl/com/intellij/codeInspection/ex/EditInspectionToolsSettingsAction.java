package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.ui.ErrorOptionsConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 * Date: Feb 7, 2005
 */
public class EditInspectionToolsSettingsAction implements IntentionAction {
  private final String myShortName;

  public EditInspectionToolsSettingsAction(LocalInspectionTool tool) {
    myShortName = tool.getShortName();
  }

  public EditInspectionToolsSettingsAction(HighlightDisplayKey key) {
    myShortName = key.toString();
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.text");
  }

  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(file.getProject());
    final boolean canChooseDifferentProfiles = !projectProfileManager.useProjectLevelProfileSettings();
    final InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
    InspectionProfileImpl inspectionProfile = (InspectionProfileImpl)(canChooseDifferentProfiles
                                                                      ? profileManager.getRootProfile()
                                                                      : projectProfileManager.getInspectionProfile(file));
    editToolSettings(project,
                     inspectionProfile,
                     canChooseDifferentProfiles,
                     myShortName,
                     canChooseDifferentProfiles ? profileManager : InspectionProjectProfileManager.getInstance(project));
  }

  public boolean editToolSettings(final Project project,
                                  final InspectionProfileImpl inspectionProfile,
                                  final boolean canChooseDifferentProfiles) {
    return editToolSettings(project,
                            inspectionProfile,
                            canChooseDifferentProfiles,
                            myShortName,
                            InspectionProjectProfileManager.getInstance(project));
  }

  public static boolean editToolSettings(final Project project,
                                         final InspectionProfileImpl inspectionProfile,
                                         final boolean canChooseDifferentProfile,
                                         final String selectedToolShortName,
                                         final ProfileManager manager) {
    @NonNls final String dimensionServiceKey = "#Errors";
    final ShowSettingsUtil settingsUtil = ShowSettingsUtil.getInstance();
    if (!canChooseDifferentProfile){
      return settingsUtil.editConfigurable(project,
                                           dimensionServiceKey,
                                           new InspectionToolsConfigurable(project,
                                                                           manager,
                                                                           inspectionProfile,
                                                                           selectedToolShortName));
    } else {
      final ErrorOptionsConfigurable errorsConfigurable = ErrorOptionsConfigurable.getInstance(project);
      return settingsUtil.editConfigurable(project,
                                           errorsConfigurable,
                                           new Runnable(){
                                             public void run() {
                                               errorsConfigurable.selectNodeInTree(inspectionProfile.getName());
                                               SwingUtilities.invokeLater(new Runnable() {
                                                 public void run() {
                                                   errorsConfigurable.selectInspectionTool(selectedToolShortName);
                                                 }
                                               });
                                             }
                                           });
    }

  }

  public boolean startInWriteAction() {
    return false;
  }

}
