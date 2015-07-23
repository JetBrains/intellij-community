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
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameFilter;
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Konstantin Bulenkov
 */
public class RunInspectionAction extends GotoActionBase {
  private static final Logger LOGGER = Logger.getInstance("#" + RunInspectionAction.class.getName());

  public RunInspectionAction() {
    getTemplatePresentation().setText(IdeBundle.message("goto.inspection.action.text"));
  }

  @Override
  protected void gotoActionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiElement psiElement = CommonDataKeys.PSI_ELEMENT.getData(e.getDataContext());
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.inspection");

    final GotoInspectionModel model = new GotoInspectionModel(project);
    showNavigationPopup(e, model, new GotoActionCallback<Object>() {
      @Override
      protected ChooseByNameFilter<Object> createFilter(@NotNull ChooseByNameViewModel popup) {
        popup.setSearchInAnyPlace(true);
        return super.createFilter(popup);
      }

      @Override
      public void elementChosen(ChooseByNameViewModel popup, final Object element) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            runInspection(project, ((InspectionToolWrapper)element).getShortName(), virtualFile, psiElement, psiFile);
          }
        });
      }
    }, false);
  }

  private static void runInspection(@NotNull Project project,
                                    @NotNull String shortName,
                                    @Nullable VirtualFile virtualFile,
                                    PsiElement psiElement,
                                    PsiFile psiFile) {
    final InspectionManagerEx managerEx = (InspectionManagerEx)InspectionManager.getInstance(project);
    final Module module = virtualFile != null ? ModuleUtilCore.findModuleForFile(virtualFile, project) : null;

    AnalysisScope analysisScope = null;
    if (psiFile != null) {
      analysisScope = new AnalysisScope(psiFile);
    }
    else {
      if (virtualFile != null && virtualFile.isDirectory()) {
        final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
        if (psiDirectory != null) {
          analysisScope = new AnalysisScope(psiDirectory);
        }
      }
      if (analysisScope == null && virtualFile != null) {
        analysisScope = new AnalysisScope(project, Arrays.asList(virtualFile));
      }
      if (analysisScope == null) {
        analysisScope = new AnalysisScope(project);
      }
    }

    final FileFilterPanel fileFilterPanel = new FileFilterPanel();
    fileFilterPanel.init();

    final BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(
      AnalysisScopeBundle.message("specify.analysis.scope", InspectionsBundle.message("inspection.action.title")),
      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
      project, analysisScope, module != null ? module.getName() : null,
      true, AnalysisUIOptions.getInstance(project), psiElement) {

      @Override
      protected JComponent getAdditionalActionSettings(Project project) {
        return fileFilterPanel.getPanel();
      }

      @NotNull
      @Override
      public AnalysisScope getScope(@NotNull AnalysisUIOptions uiOptions,
                                    @NotNull AnalysisScope defaultScope,
                                    @NotNull Project project,
                                    Module module) {
        final AnalysisScope scope = super.getScope(uiOptions, defaultScope, project, module);
        final SearchScope filterScope = fileFilterPanel.getSearchScope();
        if (filterScope == null) {
          return scope;
        }
        final SearchScope filteredScope = filterScope.intersectWith(scope.toSearchScope());
        return new AnalysisScope(filteredScope, project);
      }
    };

    if (!dialog.showAndGet()) {
      return;
    }
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    AnalysisScope scope = dialog.getScope(uiOptions, analysisScope, project, module);
    PsiElement element = psiFile == null ? psiElement : psiFile;
    final InspectionProfile currentProfile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    final InspectionToolWrapper toolWrapper = currentProfile.getInspectionTool(shortName, project);
    LOGGER.assertTrue(toolWrapper != null, "Missed inspection: " + shortName);
    RunInspectionIntention.rerunInspection(toolWrapper, managerEx, scope, element);
  }
}
