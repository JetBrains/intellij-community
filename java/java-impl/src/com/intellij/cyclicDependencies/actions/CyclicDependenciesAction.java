// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CyclicDependenciesAction extends AnAction{
  private final String myAnalysisVerb;
  private final String myAnalysisNoun;
  private final String myTitle;

  public CyclicDependenciesAction() {
    myAnalysisVerb = CodeInsightBundle.message("action.analyze.verb");
    myAnalysisNoun = CodeInsightBundle.message("action.analysis.noun");
    myTitle = JavaBundle.message("action.cyclic.dependency.title");
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(
      getInspectionScope(event.getDataContext()) != null ||
      event.getData(CommonDataKeys.PROJECT) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final Module module = PlatformCoreDataKeys.MODULE.getData(dataContext);
    AnalysisScope scope = getInspectionScope(dataContext);
    if (scope == null || scope.getScopeType() != AnalysisScope.MODULES) {
      ProjectModuleOrPackageDialog dlg = null;
      if (module != null) {
        dlg = new ProjectModuleOrPackageDialog(
          ModuleManager.getInstance(project).getModules().length == 1 ? null : ModuleUtilCore.getModuleNameInReadAction(module), scope);
        if (!dlg.showAndGet()) {
          return;
        }
      }
      if (dlg == null || dlg.isProjectScopeSelected()) {
        scope = getProjectScope(dataContext);
      }
      else if (dlg.isModuleScopeSelected()) {
        scope = getModuleScope(dataContext);
      }
      if (scope != null) {
        scope.setIncludeTestSource(dlg != null && dlg.isIncludeTestSources());
      }
    }

    FileDocumentManager.getInstance().saveAllDocuments();

    new CyclicDependenciesHandler(project, scope).analyze();
  }


  @Nullable
  private static AnalysisScope getInspectionScope(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private static AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: package, project, module.
    Project projectContext = PlatformCoreDataKeys.PROJECT_CONTEXT.getData(dataContext);
    if (projectContext != null) {
      return null;
    }

    Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (moduleContext != null) {
      return null;
    }

    final Module [] modulesArray = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modulesArray != null && modulesArray.length > 0) {
      return new AnalysisScope(modulesArray);
    }

    PsiElement psiTarget = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs.length == 0) return null;
      return new JavaAnalysisScope(pack, PlatformCoreDataKeys.MODULE.getData(dataContext));
    }

    return null;
  }

  @Nullable
  private static AnalysisScope getProjectScope(@NotNull DataContext dataContext) {
    final Project data = CommonDataKeys.PROJECT.getData(dataContext);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  @Nullable
  private static AnalysisScope getModuleScope(DataContext dataContext) {
    final Module data = PlatformCoreDataKeys.MODULE.getData(dataContext);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  private class ProjectModuleOrPackageDialog extends DialogWrapper {
    private final String myModuleName;
    private final AnalysisScope mySelectedScope;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;
    private JRadioButton mySelectedScopeButton;

    private JPanel myScopePanel;
    private JPanel myWholePanel;
    private JCheckBox myIncludeTestSourcesCb;


    ProjectModuleOrPackageDialog(String moduleName, AnalysisScope selectedScope) {
      super(true);
      myModuleName = moduleName;
      mySelectedScope = selectedScope;
      init();
      setTitle(JavaBundle.message("cyclic.dependencies.scope.dialog.title", myTitle));
      setHorizontalStretch(1.75f);
    }

    boolean isIncludeTestSources() {
      return myIncludeTestSourcesCb.isSelected();
    }

    @Override
    protected JComponent createCenterPanel() {
      myScopePanel.setBorder(IdeBorderFactory.createTitledBorder(
        CodeInsightBundle.message("analysis.scope.title", myAnalysisNoun)));
      myProjectButton.setText(JavaBundle.message("cyclic.dependencies.scope.dialog.project.button", myAnalysisVerb));
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton.setText(JavaBundle.message("cyclic.dependencies.scope.dialog.module.button", myAnalysisVerb, myModuleName));
        group.add(myModuleButton);
      }
      myModuleButton.setVisible(myModuleName != null);
      mySelectedScopeButton.setVisible(mySelectedScope != null);
      if (mySelectedScope != null) {
        mySelectedScopeButton.setText(mySelectedScope.getShortenName());
        group.add(mySelectedScopeButton);
      }
      if (mySelectedScope != null) {
        mySelectedScopeButton.setSelected(true);
      }
      else if (myModuleName != null) {
        myModuleButton.setSelected(true);
      }
      else {
        myProjectButton.setSelected(true);
      }
      return myWholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null && myModuleButton.isSelected();
    }

  }
}