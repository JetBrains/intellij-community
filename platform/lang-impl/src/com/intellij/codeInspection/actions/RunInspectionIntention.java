/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class RunInspectionIntention implements IntentionAction, HighPriorityAction {
  private final String myShortName;

  public RunInspectionIntention(final InspectionProfileEntry tool) {
    myShortName = tool.getShortName();
  }

  public RunInspectionIntention(final HighlightDisplayKey key) {
    myShortName = key.toString();
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
    final InspectionProfileEntry tool =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile().getInspectionTool(myShortName, file);
    if (tool instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)tool).isUnfair()) {
      return false;
    }
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    AnalysisScope analysisScope = new AnalysisScope(file);
    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.isPhysical() || virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      analysisScope = new AnalysisScope(project);
    }
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
                                                                      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
                                                                      project,
                                                                      analysisScope,
                                                                      module != null ? module.getName() : null,
                                                                      true, AnalysisUIOptions.getInstance(project), file);
    dlg.show();
    if (!dlg.isOK()) return;
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    analysisScope = dlg.getScope(uiOptions, analysisScope, project, module);
    final InspectionProfileEntry baseTool =
        InspectionProjectProfileManager.getInstance(project).getInspectionProfile().getInspectionTool(myShortName, file);
    rerunInspection(baseTool, managerEx, analysisScope, file);
  }

  public static void rerunInspection(final InspectionProfileEntry baseTool, final InspectionManagerEx managerEx, final AnalysisScope scope,
                              PsiElement psiElement) {
    GlobalInspectionContextImpl inspectionContext = createContext(baseTool, managerEx, psiElement);
    inspectionContext.doInspections(scope, managerEx);
  }

  public static GlobalInspectionContextImpl createContext(final InspectionProfileEntry baseTool, InspectionManagerEx managerEx, PsiElement psiElement) {
    final InspectionProfileImpl model = InspectionProfileImpl.createSimple(baseTool.getDisplayName(), baseTool);
    try {
      Element element = new Element("toCopy");
      baseTool.writeSettings(element);
      model.getInspectionTool(baseTool.getShortName(), psiElement).readSettings(element);
    }
    catch (Exception e) {
      //skip
    }
    model.setEditable(baseTool.getDisplayName());
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    inspectionContext.setExternalProfile(model);
    return inspectionContext;
  }

  public boolean startInWriteAction() {
    return false;
  }
}
