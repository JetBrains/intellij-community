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
package com.intellij.cyclicDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.JavaAnalysisScope;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 31, 2005
 */
public class CyclicDependenciesAction extends AnAction{
  private final String myAnalysisVerb;
  private final String myAnalysisNoun;
  private final String myTitle;

  public CyclicDependenciesAction() {
    myAnalysisVerb = AnalysisScopeBundle.message("action.analyze.verb");
    myAnalysisNoun = AnalysisScopeBundle.message("action.analysis.noun");
    myTitle = AnalysisScopeBundle.message("action.cyclic.dependency.title");
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(
      getInspectionScope(event.getDataContext()) != null || event.getData(LangDataKeys.PSI_FILE) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    final Module module = LangDataKeys.MODULE.getData(dataContext);
    if (project != null) {
      PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
      if (psiFile != null && !(psiFile instanceof PsiJavaFile)) {
        return;
      }
      AnalysisScope scope = getInspectionScope(dataContext);
      if (scope == null || scope.getScopeType() != AnalysisScope.MODULES){
        ProjectModuleOrPackageDialog dlg = null;
        if (module != null) {
          dlg = new ProjectModuleOrPackageDialog(ModuleUtil.getModuleNameInReadAction(module));
          dlg.show();
          if (!dlg.isOK()) return;
        }
        if (dlg == null || dlg.isProjectScopeSelected()) {
          scope = getProjectScope(dataContext);
        }
        else {
          if (dlg.isModuleScopeSelected()) {
            scope = getModuleScope(dataContext);
          }
        }
      }

      FileDocumentManager.getInstance().saveAllDocuments();

      new CyclicDependenciesHandler(project, scope).analyze();
    }
  }


  @Nullable
  private static AnalysisScope getInspectionScope(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private static AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: package, project, module.
    Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module [] modulesArray = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }

    PsiElement psiTarget = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    else if (psiTarget instanceof PsiPackage) {
      PsiPackage pack = (PsiPackage)psiTarget;
      PsiDirectory[] dirs = pack.getDirectories(GlobalSearchScope.projectScope(pack.getProject()));
      if (dirs.length == 0) return null;
      return new JavaAnalysisScope(pack, LangDataKeys.MODULE.getData(dataContext));
    } else if (psiTarget != null){
      return null;
    }


    return getProjectScope(dataContext);
  }

  @Nullable
  private static AnalysisScope getProjectScope(DataContext dataContext) {
    final Project data = PlatformDataKeys.PROJECT.getData(dataContext);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  @Nullable
  private static AnalysisScope getModuleScope(DataContext dataContext) {
    final Module data = LangDataKeys.MODULE.getData(dataContext);
    if (data == null) {
      return null;
    }
    return new AnalysisScope(data);
  }

  private class ProjectModuleOrPackageDialog extends DialogWrapper {
    private final String myModuleName;
    private JRadioButton myProjectButton;
    private JRadioButton myModuleButton;

    private JPanel myScopePanel;
    private JPanel myWholePanel;


    public ProjectModuleOrPackageDialog(String moduleName) {
      super(true);
      myModuleName = moduleName;
      init();
      setTitle(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.title", myTitle));
      setHorizontalStretch(1.75f);
      myModuleButton.setSelected(true);
    }

    protected JComponent createCenterPanel() {
      myScopePanel.setBorder(IdeBorderFactory.createTitledBorder(AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoun)));
      myProjectButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.project.button", myAnalysisVerb));
      ButtonGroup group = new ButtonGroup();
      group.add(myProjectButton);
      if (myModuleName != null) {
        myModuleButton.setText(AnalysisScopeBundle.message("cyclic.dependencies.scope.dialog.module.button", myAnalysisVerb, myModuleName));
        group.add(myModuleButton);
      }
      return myWholePanel;
    }

    public boolean isProjectScopeSelected() {
      return myProjectButton.isSelected();
    }

    public boolean isModuleScopeSelected() {
      return myModuleButton != null ? myModuleButton.isSelected() : false;
    }

  }
}
