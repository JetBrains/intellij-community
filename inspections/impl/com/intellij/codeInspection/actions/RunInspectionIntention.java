/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.UIOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class RunInspectionIntention implements IntentionAction {
  private LocalInspectionTool myTool;

  public RunInspectionIntention(final LocalInspectionTool tool) {
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
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfileImpl profile = new InspectionProfileImpl(profileManager.getProfileName(file));
    final ModifiableModel model = profile.getModifiableModel();
    final InspectionProfileEntry[] profileEntries = model.getInspectionTools();
    model.patchTool(profileManager.getInspectionProfile(file).getInspectionTool(myTool.getShortName()));
    for (InspectionProfileEntry entry : profileEntries) {
      model.disableTool(entry.getShortName());
    }
    model.enableTool(myTool.getShortName());
    model.setEditable(myTool.getDisplayName());
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
                                                                      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
                                                                      project, AnalysisScopeBundle.message("scope.file",
                                                                                                           VfsUtil.calcRelativeToProjectPath(
                                                                                                             file.getVirtualFile(),
                                                                                                             file.getProject())),
                                                                      module != null ? module.getName() : null,
                                                                      true);
    AnalysisScope scope = new AnalysisScope(file);
    dlg.show();
    if (!dlg.isOK()) return;
    final UIOptions uiOptions = ((InspectionManagerEx)InspectionManagerEx.getInstance(project)).getUIOptions();
    scope = dlg.getScope(uiOptions, scope, project, module);
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    inspectionContext.setExternalProfile((InspectionProfile)model);
    inspectionContext.RUN_WITH_EDITOR_PROFILE = false;
    inspectionContext.doInspections(scope, managerEx);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        inspectionContext.setExternalProfile(null);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
