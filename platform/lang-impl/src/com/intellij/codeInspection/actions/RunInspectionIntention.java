/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

/**
 * User: anna
 * Date: 21-Feb-2006
 */
public class RunInspectionIntention implements IntentionAction, HighPriorityAction {
  private final static Logger LOG = Logger.getInstance(RunInspectionIntention.class);

  private final String myShortName;

  public RunInspectionIntention(@NotNull InspectionToolWrapper toolWrapper) {
    myShortName = toolWrapper.getShortName();
  }

  public RunInspectionIntention(final HighlightDisplayKey key) {
    myShortName = key.toString();
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("run.inspection.on.file.intention.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return LocalInspectionToolWrapper.findTool2RunInBatch(project, file, myShortName) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Module module = file != null ? ModuleUtilCore.findModuleForPsiElement(file) : null;
    AnalysisScope analysisScope = new AnalysisScope(project);
    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (file.isPhysical() && virtualFile != null && virtualFile.isInLocalFileSystem()) {
        analysisScope = new AnalysisScope(file);
      }
    }

    selectScopeAndRunInspection(myShortName, analysisScope, module, file, project);
  }

  public static void selectScopeAndRunInspection(@NotNull String toolShortName,
                                                 @NotNull AnalysisScope customScope,
                                                 @Nullable Module module,
                                                 @Nullable PsiElement context,
                                                 @NotNull Project project) {
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(
      AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
      project,
      customScope,
      module != null ? module.getName() : null,
      true, AnalysisUIOptions.getInstance(project), context);
    if (!dlg.showAndGet()) {
      return;
    }
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    customScope = dlg.getScope(uiOptions, customScope, project, module);
    final InspectionToolWrapper wrapper = LocalInspectionToolWrapper.findTool2RunInBatch(project, context, toolShortName);
    LOG.assertTrue(wrapper != null, "Can't find tool with name = \"" + toolShortName + "\"");
    rerunInspection(wrapper, (InspectionManagerEx)InspectionManager.getInstance(project), customScope, context);
  }

  public static void rerunInspection(@NotNull InspectionToolWrapper toolWrapper,
                                     @NotNull InspectionManagerEx managerEx,
                                     @NotNull AnalysisScope scope,
                                     @Nullable PsiElement psiElement) {
    GlobalInspectionContextImpl inspectionContext = createContext(toolWrapper, managerEx, psiElement);
    inspectionContext.doInspections(scope);
  }

  @NotNull
  public static GlobalInspectionContextImpl createContext(@NotNull InspectionToolWrapper toolWrapper,
                                                          @NotNull InspectionManagerEx managerEx,
                                                          @Nullable PsiElement psiElement) {
    final InspectionProfileImpl model = createProfile(toolWrapper, managerEx, psiElement);
    final GlobalInspectionContextImpl inspectionContext = managerEx.createNewGlobalContext(false);
    inspectionContext.setExternalProfile(model);
    return inspectionContext;
  }

  @NotNull
  public static InspectionProfileImpl createProfile(@NotNull InspectionToolWrapper toolWrapper,
                                                    @NotNull InspectionManagerEx managerEx,
                                                    @Nullable PsiElement psiElement) {
    final InspectionProfileImpl rootProfile = (InspectionProfileImpl)InspectionProfileManager.getInstance().getRootProfile();
    LinkedHashSet<InspectionToolWrapper> allWrappers = new LinkedHashSet<InspectionToolWrapper>();
    allWrappers.add(toolWrapper);
    rootProfile.collectDependentInspections(toolWrapper, allWrappers, managerEx.getProject());
    InspectionToolWrapper[] toolWrappers = allWrappers.toArray(new InspectionToolWrapper[allWrappers.size()]);
    final InspectionProfileImpl model = InspectionProfileImpl.createSimple(toolWrapper.getDisplayName(), managerEx.getProject(), toolWrappers);
    try {
      Element element = new Element("toCopy");
      for (InspectionToolWrapper wrapper : toolWrappers) {
        wrapper.getTool().writeSettings(element);
        InspectionToolWrapper tw = psiElement == null ? model.getInspectionTool(wrapper.getShortName(), managerEx.getProject())
                                                      : model.getInspectionTool(wrapper.getShortName(), psiElement);
        tw.getTool().readSettings(element);
      }
    }
    catch (WriteExternalException ignored) {
    }
    catch (InvalidDataException ignored) {
    }
    model.setSingleTool(toolWrapper.getShortName());
    return model;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
