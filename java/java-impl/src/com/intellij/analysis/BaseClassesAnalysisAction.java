/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
public abstract class BaseClassesAnalysisAction extends BaseAnalysisAction {
  protected BaseClassesAnalysisAction(String title, String analysisNoon) {
    super(title, analysisNoon);
  }

  protected abstract void analyzeClasses(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull ProgressIndicator indicator);

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    FileDocumentManager.getInstance().saveAllDocuments();

    ProgressManager.getInstance().run(new Task.Backgroundable(project, AnalysisScopeBundle.message("analyzing.project"), true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.setText(AnalysisScopeBundle.message("checking.class.files"));

        if (project.isDisposed()) {
          return;
        }
        final Set<Module> modules = getScopeModules(scope);
        final CompilerManager compilerManager = CompilerManager.getInstance(project);
        final CompileScope compileScope = compilerManager.createModulesCompileScope(modules.toArray(Module.EMPTY_ARRAY), false);
        final boolean upToDate = compilerManager.isUpToDate(compileScope);

        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed()) {
            return;
          }
          if (!upToDate) {
            final int i = Messages.showYesNoCancelDialog(getProject(), AnalysisScopeBundle.message("recompile.confirmation.message"),
                                                         AnalysisScopeBundle.message("project.is.out.of.date"), Messages.getWarningIcon());

            if (i == Messages.CANCEL) return;

            if (i == Messages.YES) {
              compileAndAnalyze(project, scope);
            }
            else {
              doAnalyze(project, scope);
            }
          }
          else {
            doAnalyze(project, scope);
          }
        });
      }
    });
  }

  /**
   * @param scope  the scope of which to return the modules
   * @return the modules contained by the scope and/or the module containing the scope.
   */
  @NotNull
  public static Set<Module> getScopeModules(@NotNull AnalysisScope scope) {
    final int scopeType = scope.getScopeType();
    final Set<Module> result = new HashSet<>();
    final Project project = scope.getProject();
    if (scopeType == AnalysisScope.MODULE) {
      final Module module = scope.getModule();
      if (module != null) {
        result.add(module);
      }
    }
    else if (scopeType == AnalysisScope.MODULES) {
      result.addAll(scope.getModules());
    }
    else if (scopeType == AnalysisScope.FILE) {
      final PsiElement element = scope.getElement();
      if (element != null) {
        final VirtualFile vFile = ((PsiFileSystemItem)element).getVirtualFile();
        getContainingModule(vFile, project, result);
      }
    }
    else if (scopeType == AnalysisScope.DIRECTORY) {
      final PsiElement element = scope.getElement();
      if (element != null) {
        final VirtualFile vFile = ((PsiFileSystemItem)element).getVirtualFile();
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        getModulesContainedInDirectory(modules, vFile, result);
        getContainingModule(vFile, project, result);
      }
    }
    else if (scopeType == AnalysisScope.VIRTUAL_FILES) {
      final Set<VirtualFile> files = scope.getFiles();
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (VirtualFile vFile : files) {
        getModulesContainedInDirectory(modules, vFile, result);
        getContainingModule(vFile, project, result);
      }
    }
    else {
      result.addAll(Arrays.asList(ModuleManager.getInstance(project).getModules()));
    }
    return result;
  }

  private static void getContainingModule(VirtualFile vFile, @NotNull Project project, Set<Module> result) {
    final ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);
    final Module module = fileIndex.getModuleForFile(vFile);
    if (module != null) {
      result.add(module);
    }
  }

  private static void getModulesContainedInDirectory(Module[] modules, VirtualFile directory, Set<Module> result) {
    if (!directory.isDirectory()) {
      return;
    }
    outer: for (Module module : modules) {
      final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile root : roots) {
        if (VfsUtilCore.isAncestor(directory, root, false)) {
          result.add(module);
          continue outer;
        }
      }
    }
  }

  private void doAnalyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, AnalysisScopeBundle.message("analyzing.project"), true) {
      @Override
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Analysis",  "\"" + getTitle() + "\" Analysis Finished", "");
      }

      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        analyzeClasses(project, scope, indicator);
      }
    });
  }

  private void compileAndAnalyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    if (project.isDisposed()) {
      return;
    }
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    compilerManager.make(compilerManager.createProjectCompileScope(project), new CompileStatusNotification() {
      @Override
      public void finished(final boolean aborted, final int errors, final int warnings, final CompileContext compileContext) {
        if (aborted || errors != 0) return;
        ApplicationManager.getApplication().invokeLater(() -> doAnalyze(project, scope));
    }});
  }
}
