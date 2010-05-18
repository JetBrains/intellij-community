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

package com.intellij.analysis;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(getInspectionScope(event.getDataContext()) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project == null) {
      return;
    }
    AnalysisScope scope = getInspectionScope(dataContext);
    LOG.assertTrue(scope != null);
    /*if (scope.getScopeType() == AnalysisScope.VIRTUAL_FILES){
    FileDocumentManager.getInstance().saveAllDocuments();
    analyze(project, scope);
    return;
  }*/
    final boolean rememberScope = e.getPlace().equals(ActionPlaces.MAIN_MENU);
    final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", myTitle),
                                                                AnalysisScopeBundle.message("analysis.scope.title", myAnalysisNoon),
                                                                project,
                                                                scope,
                                                                module != null && scope.getScopeType() != AnalysisScope.MODULE ? ModuleUtil
                                                                  .getModuleNameInReadAction(module) : null,
                                                                rememberScope, AnalysisUIOptions.getInstance(project), element){
      @Nullable
      protected JComponent getAdditionalActionSettings(final Project project) {
        return BaseAnalysisAction.this.getAdditionalActionSettings(project, this);
      }


      protected void doHelpAction() {
        HelpManager.getInstance().invokeHelp(getHelpTopic());
      }

      protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
      }
    };
    dlg.show();
    if (!dlg.isOK()) {
      canceled();
      return;
    }
    final int oldScopeType = uiOptions.SCOPE_TYPE;
    scope = dlg.getScope(uiOptions, scope, project, module);
    if (!rememberScope){
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

  protected abstract void analyze(@NotNull Project project, AnalysisScope scope);

  @Nullable
  private AnalysisScope getInspectionScope(final DataContext dataContext) {
    if (PlatformDataKeys.PROJECT.getData(dataContext) == null) return null;

    AnalysisScope scope = getInspectionScopeImpl(dataContext);

    return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  @Nullable
  private AnalysisScope getInspectionScopeImpl(DataContext dataContext) {
    //Possible scopes: file, directory, package, project, module.
    Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    final AnalysisScope analysisScope = AnalysisScope.KEY.getData(dataContext);
    if (analysisScope != null) {
      return analysisScope;
    }

    Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (moduleContext != null) {
      return new AnalysisScope(moduleContext);
    }

    Module [] modulesArray = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
    if (modulesArray != null) {
      return new AnalysisScope(modulesArray);
    }
    final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
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

    PsiElement psiTarget = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (psiTarget instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)psiTarget;
      if (!acceptNonProjectDirectories() && !psiDirectory.getManager().isInProject(psiDirectory)) return null;
      return new AnalysisScope(psiDirectory);
    }
    else if (psiTarget != null) {
      return null;
    }

    final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (virtualFiles != null) { //analyze on selection
      final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      Set<VirtualFile> files = new HashSet<VirtualFile>();
      for (VirtualFile vFile : virtualFiles) {
        if (fileIndex.isInContent(vFile)) {
          if (vFile instanceof VirtualFileWindow) {
            files.add(vFile);
            vFile = ((VirtualFileWindow)vFile).getDelegate();
          }
          traverseDirectory(vFile, files);
        }
      }
      return new AnalysisScope(project, files);
    }
    return getProjectScope(dataContext);
  }

  protected boolean acceptNonProjectDirectories() {
    return false;
  }

  private static AnalysisScope getProjectScope(DataContext dataContext) {
    return new AnalysisScope(PlatformDataKeys.PROJECT.getData(dataContext));
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog){
    return null;
  }

  private static void traverseDirectory(VirtualFile vFile, Set<VirtualFile> files) {
    if (vFile.isDirectory()) {
      final VirtualFile[] virtualFiles = vFile.getChildren();
      for (VirtualFile virtualFile : virtualFiles) {
        traverseDirectory(virtualFile, files);
      }
    }
    else {
      files.add(vFile);
    }
  }
}
