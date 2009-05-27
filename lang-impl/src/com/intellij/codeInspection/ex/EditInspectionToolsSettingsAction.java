package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.IDEInspectionToolsConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
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

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(file.getProject());
    InspectionProfile inspectionProfile = projectProfileManager.getInspectionProfile();
    editToolSettings(project,
                     inspectionProfile, true,
                     myShortName);
  }

  public boolean editToolSettings(final Project project,
                                  final InspectionProfileImpl inspectionProfile,
                                  final boolean canChooseDifferentProfiles) {
    return editToolSettings(project,
                            inspectionProfile,
                            canChooseDifferentProfiles,
                            myShortName);
  }

  public static boolean editToolSettings(final Project project,
                                         final InspectionProfile inspectionProfile,
                                         final boolean canChooseDifferentProfile,
                                         final String selectedToolShortName) {
    final ShowSettingsUtil settingsUtil = ShowSettingsUtil.getInstance();
    final ErrorsConfigurable errorsConfigurable;
    if (!canChooseDifferentProfile) {
      errorsConfigurable = new IDEInspectionToolsConfigurable(InspectionProjectProfileManager.getInstance(project), InspectionProfileManager.getInstance());
    }
    else {
      errorsConfigurable = ErrorsConfigurable.SERVICE.getInstance(project);
    }
    return settingsUtil.editConfigurable(project, errorsConfigurable, new Runnable() {
      public void run() {
        errorsConfigurable.selectProfile(inspectionProfile.getName());
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            errorsConfigurable.selectInspectionTool(selectedToolShortName);
          }
        });
      }
    });

  }

  public boolean startInWriteAction() {
    return false;
  }

}
