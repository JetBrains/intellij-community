/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class RunInspectionIntention implements IntentionAction {
  private String myShortName;
  private String myDisplayName;

  public RunInspectionIntention(final LocalInspectionTool tool) {
    myShortName = tool.getShortName();
    myDisplayName = tool.getDisplayName();
  }

  public RunInspectionIntention(final HighlightDisplayKey key) {
    myShortName = key.toString();
    myDisplayName = HighlightDisplayKey.getDisplayNameByKey(key);
  }

  @NotNull
  public String getText() {
    return InspectionsBundle.message("run.inspection.on.file.intention.text");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionManagerEx managerEx = ((InspectionManagerEx)InspectionManagerEx.getInstance(project));
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
                                                                      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
                                                                      project,
                                                                      new AnalysisScope(file),
                                                                      module != null ? module.getName() : null,
                                                                      true);
    AnalysisScope scope = new AnalysisScope(file);
    dlg.show();
    if (!dlg.isOK()) return;
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    scope = dlg.getScope(uiOptions, scope, project, module);
    rerunInspection(managerEx, scope);
  }

  public void rerunInspection(final InspectionManagerEx managerEx, final AnalysisScope scope) {
    final InspectionProfileImpl profile = new InspectionProfileImpl(myDisplayName);
    final ModifiableModel model = profile.getModifiableModel();
    final InspectionProfileEntry[] profileEntries = model.getInspectionTools();
    for (InspectionProfileEntry entry : profileEntries) {
      model.disableTool(entry.getShortName());
    }
    model.enableTool(myShortName);
    model.setEditable(myDisplayName);
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    inspectionContext.setExternalProfile((InspectionProfile)model);
    inspectionContext.RUN_WITH_EDITOR_PROFILE = false;
    inspectionContext.doInspections(scope, managerEx);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
