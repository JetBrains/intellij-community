package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

public class DisableInspectionToolAction implements IntentionAction {
  private final String myToolId;

  public DisableInspectionToolAction(LocalInspectionTool tool) {
    myToolId = tool.getID();
  }

  public String getText() {
    return InspectionsBundle.message("disable.inspection.action.name");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    InspectionProfileImpl inspectionProfile = InspectionProjectProfileManager.getInstance(file.getProject()).getProfile((PsiElement)file);
    InspectionProfile.ModifiableModel model = inspectionProfile.getModifiableModel();
    model.disableTool(myToolId);
    model.commit();
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
