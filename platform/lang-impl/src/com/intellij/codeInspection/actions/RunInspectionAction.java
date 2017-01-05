/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.GotoActionBase;
import com.intellij.ide.util.gotoByName.ChooseByNameFilter;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
/**
 * @author Konstantin Bulenkov
 */
public class RunInspectionAction extends GotoActionBase {
  private static final Logger LOGGER = Logger.getInstance(RunInspectionAction.class);

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
      protected ChooseByNameFilter<Object> createFilter(@NotNull ChooseByNamePopup popup) {
        popup.setSearchInAnyPlace(true);
        return super.createFilter(popup);
      }

      @Override
      public void elementChosen(ChooseByNamePopup popup, final Object element) {
        ApplicationManager.getApplication().invokeLater(
          () -> runInspection(project, ((InspectionToolWrapper)element).getShortName(), virtualFile, psiElement, psiFile));
      }
    }, false);
  }

  public static void runInspection(final @NotNull Project project,
                                   @NotNull String shortName,
                                   @Nullable VirtualFile virtualFile,
                                   PsiElement psiElement,
                                   PsiFile psiFile) {
    final PsiElement element = psiFile == null ? psiElement : psiFile;
    final InspectionProfile currentProfile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    final InspectionToolWrapper toolWrapper = element != null ? currentProfile.getInspectionTool(shortName, element)
                                                              : currentProfile.getInspectionTool(shortName, project);
    LOGGER.assertTrue(toolWrapper != null, "Missed inspection: " + shortName);

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

    final AnalysisUIOptions options = AnalysisUIOptions.getInstance(project);
    final FileFilterPanel fileFilterPanel = new FileFilterPanel();
    fileFilterPanel.init(options);

    final AnalysisScope initialAnalysisScope = analysisScope;
    final BaseAnalysisActionDialog dialog = new BaseAnalysisActionDialog(
      "Run '" + toolWrapper.getDisplayName() + "'",
      AnalysisScopeBundle.message("analysis.scope.title", InspectionsBundle.message("inspection.action.noun")),
      project, analysisScope, module != null ? module.getName() : null,
      true, options, psiElement) {

      private InspectionToolWrapper myUpdatedSettingsToolWrapper;

      @Nullable
      @Override
      protected JComponent getAdditionalActionSettings(Project project) {
        final JPanel fileFilter = fileFilterPanel.getPanel();
        if (toolWrapper.getTool().createOptionsPanel() != null) {
          JPanel additionPanel = new JPanel();
          additionPanel.setLayout(new BoxLayout(additionPanel, BoxLayout.Y_AXIS));
          additionPanel.add(fileFilter);
          myUpdatedSettingsToolWrapper = copyToolWithSettings(toolWrapper);//new InheritOptionsForToolPanel(toolWrapper.getShortName(), project);
          additionPanel.add(new TitledSeparator(IdeBundle.message("goto.inspection.action.choose.inherit.settings.from")));
          JComponent optionsPanel = myUpdatedSettingsToolWrapper.getTool().createOptionsPanel();
          LOGGER.assertTrue(optionsPanel != null);
          if (UIUtil.hasScrollPane(optionsPanel)) {
            additionPanel.add(optionsPanel);
          }
          else {
            additionPanel.add(ScrollPaneFactory.createScrollPane(optionsPanel, SideBorder.NONE));
          }
          return additionPanel;
        } else {
          return fileFilter;
        }
      }

      @NotNull
      @Override
      public AnalysisScope getScope(@NotNull AnalysisUIOptions uiOptions,
                                    @NotNull AnalysisScope defaultScope,
                                    @NotNull Project project,
                                    Module module) {
        final AnalysisScope scope = super.getScope(uiOptions, defaultScope, project, module);
        final GlobalSearchScope filterScope = fileFilterPanel.getSearchScope();
        if (filterScope == null) {
          return scope;
        }
        scope.setFilter(filterScope);
        return scope;
      }

      private AnalysisScope getScope() {
        return getScope(options, initialAnalysisScope, project, module);
      }

      private InspectionToolWrapper getToolWrapper() {
        return myUpdatedSettingsToolWrapper == null ? toolWrapper : myUpdatedSettingsToolWrapper;
      }

      @NotNull
      @Override
      protected Action[] createActions() {
        final List<Action> actions = new ArrayList<>();
        final boolean hasFixAll = toolWrapper.getTool() instanceof CleanupLocalInspectionTool;
        actions.add(new AbstractAction(hasFixAll ? AnalysisScopeBundle.message("action.analyze.verb")
                                                 : CommonBundle.getOkButtonText()) {
          {
            putValue(DEFAULT_ACTION, Boolean.TRUE);
          }
          @Override
          public void actionPerformed(ActionEvent e) {
            RunInspectionIntention.rerunInspection(getToolWrapper(), managerEx, getScope(), null);
            close(DialogWrapper.OK_EXIT_CODE);
          }
        });
        if (hasFixAll) {
          actions.add(new AbstractAction("Fix All") {
            @Override
            public void actionPerformed(ActionEvent e) {
              InspectionToolWrapper wrapper = getToolWrapper();
              InspectionProfileImpl cleanupToolProfile = RunInspectionIntention.createProfile(wrapper, managerEx, null);
              managerEx.createNewGlobalContext(false)
                .codeCleanup(getScope(), cleanupToolProfile, "Cleanup by " + wrapper.getDisplayName(), null, false);
              close(DialogWrapper.OK_EXIT_CODE);
            }
          });
        }
        actions.add(getCancelAction());
        if (SystemInfo.isMac) {
          Collections.reverse(actions);
        }
        return actions.toArray(new Action[actions.size()]);
      }
    };

    dialog.showAndGet();
  }

  private static InspectionToolWrapper copyToolWithSettings(@NotNull final InspectionToolWrapper tool) {
    final Element options = new Element("copy");
    tool.getTool().writeSettings(options);
    final InspectionToolWrapper copiedTool = tool.createCopy();
    try {
      copiedTool.getTool().readSettings(options);
    }
    catch (InvalidDataException e) {
      throw new RuntimeException(e);
    }
    return copiedTool;
  }
}
