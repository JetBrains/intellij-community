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

package com.intellij.analysis;

import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseAnalysisAction extends AnAction {
  private final String myTitle;
  private final String myAnalysisNoon;
  private static final Logger LOG = Logger.getInstance("#com.intellij.analysis.BaseAnalysisAction");

  protected BaseAnalysisAction(String title, String analysisNoon) {
    myTitle = title;
    myAnalysisNoon = analysisNoon;
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = event.getProject();
    final boolean dumbMode = project == null || DumbService.getInstance(project).isDumb();
    presentation.setEnabled(!dumbMode && getInspectionScope(dataContext) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    DataContext dataContext = e.getDataContext();
    Module module = getModuleFromContext(dataContext);
    AnalysisScope scope = getInspectionScope(dataContext);
    LOG.assertTrue(scope != null);
    final boolean rememberScope = ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", myTitle),
                                                                AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoon),
                                                                project,
                                                                scope,
                                                                module,
                                                                rememberScope, AnalysisUIOptions.getInstance(project), element) {
      @Override
      @Nullable
      protected JComponent getAdditionalActionSettings(final Project project) {
        return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
      }


      @Override
      protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(getHelpTopic());
      }

      @NotNull
      @Override
      protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
      }
    };
    if (!dlg.showAndGet()) {
      canceled();
      return;
    }
    final int oldScopeType = uiOptions.SCOPE_TYPE;
    scope = dlg.getScope(uiOptions, scope, project, module);
    if (!rememberScope) {
      uiOptions.SCOPE_TYPE = oldScopeType;
    }
    uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
    FileDocumentManager.getInstance().saveAllDocuments();

    analyze(project, scope);
  }

  @NonNls
  protected String getHelpTopic() {
    return "reference.dialogs.analyzeDependencies.scope";
  }

  protected void canceled() {
  }

  protected abstract void analyze(@NotNull Project project, @NotNull AnalysisScope scope);

  @Nullable
  private AnalysisScope getInspectionScope(@NotNull DataContext dataContext) {
    if (CommonDataKeys.PROJECT.getData(dataContext) == null) return null;
    AnalysisScope scope = getFileScopeFromInspectionView(dataContext);
    if (scope != null) return scope;
    scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private AnalysisScope getInspectionScopeImpl(@NotNull DataContext dataContext) {
    //Possible scopes: file, directory, package, project, module.
    Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    final AnalysisScope analysisScope = AnalysisScopeUtil.KEY.getData(dataContext);
    if (analysisScope != null) {
      return analysisScope;
    }

    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile != null && psiFile.getManager().isInProject(psiFile)) {
      final VirtualFile file = psiFile.getVirtualFile();
      if (file != null && file.isValid() && file.getFileType() instanceof ArchiveFileType && acceptNonProjectDirectories()) {
        final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file);
        if (jarRoot != null) {
          PsiDirectory psiDirectory = psiFile.getManager().findDirectory(jarRoot);
          if (psiDirectory != null) {
            return new AnalysisScope(psiDirectory);
          }
        }
      }
      return new AnalysisScope(psiFile);
    }

    VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (virtualFiles != null && project != null) { //analyze on selection
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (virtualFiles.length == 1) {
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFiles[0]);
        if (psiDirectory != null && (acceptNonProjectDirectories() || psiDirectory.getManager().isInProject(psiDirectory))) {
          return new AnalysisScope(psiDirectory);
        }
      }
      Set<VirtualFile> files = new HashSet<>();
      for (VirtualFile vFile : virtualFiles) {
        if (fileIndex.isInContent(vFile)) {
          files.add(vFile);
        }
      }
      return new AnalysisScope(project, files);
    }

    Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module[] modulesArray = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }
    return project == null ? null : new AnalysisScope(project);
  }

  protected boolean acceptNonProjectDirectories() {
    return false;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog){
    return null;
  }

  private static AnalysisScope getFileScopeFromInspectionView(DataContext dataContext) {
    InspectionResultsView inspectionView = dataContext.getData(InspectionResultsView.DATA_KEY);
    if (inspectionView != null) {
      AnalysisScope scope = inspectionView.getScope();
      int type = scope.getScopeType();
      if (type != AnalysisScope.MODULE && type != AnalysisScope.PROJECT && scope.isValid()) {
        return scope;
      }
    }
    return null;
  }

  @Nullable
  private static Module getModuleFromContext(@NotNull DataContext dataContext) {
    InspectionResultsView inspectionView = dataContext.getData(InspectionResultsView.DATA_KEY);
    if (inspectionView != null) {
      AnalysisScope scope = inspectionView.getScope();
      if (scope.getScopeType() == AnalysisScope.MODULE && scope.isValid()) {
        return scope.getModule();
      }
    }

    return dataContext.getData(LangDataKeys.MODULE);
  }
}
