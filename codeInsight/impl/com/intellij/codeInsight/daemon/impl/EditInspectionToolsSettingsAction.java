package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolsPanel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

/**
 * User: anna
 * Date: Feb 7, 2005
 */
public class EditInspectionToolsSettingsAction implements IntentionAction {
  private final String myDisplayName;
  private final String myShortName;

  public EditInspectionToolsSettingsAction(LocalInspectionTool tool) {
    myDisplayName = tool.getDisplayName();
    myShortName = tool.getShortName();
  }

  public EditInspectionToolsSettingsAction(HighlightDisplayKey key) {
    myDisplayName = HighlightDisplayKey.getDisplayNameByKey(key);
    myShortName = key.toString();
  }

  public String getText() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.text", myDisplayName);
  }

  public String getFamilyName() {
    return InspectionsBundle.message("edit.options.of.reporter.inspection.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InspectionProfileImpl inspectionProfile =
      (InspectionProfileImpl)InspectionProjectProfileManager.getInstance(file.getProject()).getProfile(file);
    editToolSettings(project, inspectionProfile, true);
  }

  public boolean editToolSettings(final Project project, final InspectionProfileImpl inspectionProfile, final boolean editorHighlighting){
    return editToolSettings(project, inspectionProfile, editorHighlighting, myShortName, InspectionProjectProfileManager.getInstance(project));
  }

  public static boolean editToolSettings(final Project project, final InspectionProfileImpl inspectionProfile, final boolean canChooseDifferentProfile, final String selectedToolShortName, final ProfileManager manager) {
    final boolean isOK = ShowSettingsUtil.getInstance().editConfigurable(project,
                                                                         "#com.intellij.codeInsight.daemon.impl.EditInspectionToolsSettingsAction",
                                                                         new Configurable(){
                                                                           private InspectionToolsPanel myPanel = new InspectionToolsPanel(inspectionProfile.getName(),
                                                                                                                                           project,
                                                                                                                                           canChooseDifferentProfile,
                                                                                                                                           manager);
                                                                           public String getDisplayName() {
                                                                             final String title = ApplicationBundle.message("title.errors");
                                                                             return canChooseDifferentProfile ? title : (title + ": \'" + inspectionProfile.getName() + "\' inspection profile");
                                                                           }

                                                                           public Icon getIcon() {
                                                                             return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
                                                                           }

                                                                           public String getHelpTopic() {
                                                                             return "preferences.errorHighlight";
                                                                           }

                                                                           public JComponent createComponent() {
                                                                             if (selectedToolShortName != null) {
                                                                               myPanel.selectInspectionTool(selectedToolShortName);
                                                                             }
                                                                             return myPanel;
                                                                           }

                                                                           public boolean isModified() {
                                                                             return myPanel.isModified();
                                                                           }

                                                                           public void apply() throws ConfigurationException {
                                                                             myPanel.apply();
                                                                             final InspectionProfileImpl editedProfile = (InspectionProfileImpl) myPanel.getSelectedProfile();
                                                                             if (canChooseDifferentProfile){
                                                                               InspectionProfileManager.getInstance().setRootProfile(editedProfile.getName());
                                                                             }
                                                                             inspectionProfile.copyFrom(editedProfile);
                                                                           }

                                                                           public void reset() {
                                                                             myPanel.reset();
                                                                           }

                                                                           public void disposeUIResources() {
                                                                             if (myPanel != null) {
                                                                               myPanel.saveVisibleState();
                                                                               myPanel = null;
                                                                             }
                                                                           }
                                                                         });
    return isOK;
  }

  public boolean startInWriteAction() {
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final EditInspectionToolsSettingsAction that = (EditInspectionToolsSettingsAction)o;

    if (myDisplayName != null ? !myDisplayName.equals(that.myDisplayName) : that.myDisplayName != null) return false;
    if (myShortName != null ? !myShortName.equals(that.myShortName) : that.myShortName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myDisplayName != null ? myDisplayName.hashCode() : 0);
    result = 29 * result + (myShortName != null ? myShortName.hashCode() : 0);
    return result;
  }
}
