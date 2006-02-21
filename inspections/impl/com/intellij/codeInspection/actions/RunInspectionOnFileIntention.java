/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ModifiableModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class RunInspectionOnFileIntention implements IntentionAction {
  private LocalInspectionTool myTool;

  public RunInspectionOnFileIntention(final LocalInspectionTool tool) {
    myTool = tool;
  }

  public String getText() {
    return InspectionsBundle.message("run.inspection.on.file.intention.text");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(project));
    final InspectionProfileImpl profile = new InspectionProfileImpl(InspectionProjectProfileManager.getInstance(project).getProfileName(file));
    final ModifiableModel model = profile.getModifiableModel();
    final InspectionProfileEntry[] profileEntries = model.getInspectionTools();
    for (InspectionProfileEntry entry : profileEntries) {
      model.disableTool(entry.getShortName());
    }
    model.enableTool(myTool.getShortName());
    model.setEditable(false);
    managerEx.setExternalProfile((InspectionProfile)model);
    managerEx.RUN_WITH_EDITOR_PROFILE = false;
    managerEx.doInspections(new AnalysisScope(file));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        managerEx.setExternalProfile(null);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
