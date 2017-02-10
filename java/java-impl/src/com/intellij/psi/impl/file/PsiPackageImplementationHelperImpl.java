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
package com.intellij.psi.impl.file;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PackagePrefixElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PsiPackageImplementationHelperImpl extends PsiPackageImplementationHelper {
  @NotNull
  @Override
  public GlobalSearchScope adjustAllScope(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope globalSearchScope) {
    return NonClasspathClassFinder.addNonClasspathScope(psiPackage.getProject(), globalSearchScope);
  }

  @NotNull
  @Override
  public VirtualFile[] occursInPackagePrefixes(@NotNull PsiPackage psiPackage) {
    List<VirtualFile> result = new ArrayList<>();
    final Module[] modules = ModuleManager.getInstance(psiPackage.getProject()).getModules();

    String qualifiedName = psiPackage.getQualifiedName();
    for (final Module module : modules) {
      for (final ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        final List<SourceFolder> sourceFolders = contentEntry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES);
        for (final SourceFolder sourceFolder : sourceFolders) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(qualifiedName)) {
            final VirtualFile file = sourceFolder.getFile();
            if (file != null) {
              result.add(file);
            }
          }
        }
      }
    }

    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public void handleQualifiedNameChange(@NotNull final PsiPackage psiPackage, @NotNull final String newQualifiedName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final String oldQualifedName = psiPackage.getQualifiedName();
    final boolean anyChanged = changePackagePrefixes(psiPackage, oldQualifedName, newQualifiedName);
    if (anyChanged) {
      UndoManager.getInstance(psiPackage.getProject()).undoableActionPerformed(new GlobalUndoableAction() {
        @Override
        public void undo() {
          changePackagePrefixes(psiPackage, newQualifiedName, oldQualifedName);
        }

        @Override
        public void redo() {
          changePackagePrefixes(psiPackage, oldQualifedName, newQualifiedName);
        }
      });
    }
  }

  private static boolean changePackagePrefixes(@NotNull PsiPackage psiPackage, @NotNull String oldQualifiedName, @NotNull String newQualifiedName) {
    final Module[] modules = ModuleManager.getInstance(psiPackage.getProject()).getModules();
    List<ModifiableRootModel> modelsToCommit = new ArrayList<>();
    for (final Module module : modules) {
      boolean anyChange = false;
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      for (final ContentEntry contentEntry : rootModel.getContentEntries()) {
        for (final SourceFolder sourceFolder : contentEntry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(oldQualifiedName)) {
            sourceFolder.setPackagePrefix(newQualifiedName + packagePrefix.substring(oldQualifiedName.length()));
            anyChange = true;
          }
        }
      }
      if (anyChange) {
        modelsToCommit.add(rootModel);
      } else {
        rootModel.dispose();
      }
    }

    if (!modelsToCommit.isEmpty()) {
      ModifiableRootModel[] rootModels = modelsToCommit.toArray(new ModifiableRootModel[modelsToCommit.size()]);
      if (rootModels.length > 0) {
        ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels[0].getProject()).getModifiableModel());
      }
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void navigate(@NotNull final PsiPackage psiPackage, final boolean requestFocus) {
    final Project project = psiPackage.getProject();
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    window.activate(null);
    window.getActivation().doWhenDone(() -> {
      final ProjectView projectView = ProjectView.getInstance(project);
      PsiDirectory[] directories = suggestMostAppropriateDirectories(psiPackage);
      if (directories.length == 0) return;
      projectView.select(directories[0], directories[0].getVirtualFile(), requestFocus);
    });
  }

  @NotNull
  private static PsiDirectory[] suggestMostAppropriateDirectories(@NotNull PsiPackage psiPackage) {
    final Project project = psiPackage.getProject();
    PsiDirectory[] directories = null;
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      final Document document = editor.getDocument();
      final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
        if (module != null) {
          final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiFile);
          final boolean isInTests =
            virtualFile != null && ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile);
          if (isInTests) {
            directories = psiPackage.getDirectories(GlobalSearchScope.moduleTestsWithDependentsScope(module));
          }

          if (directories == null || directories.length == 0) {
            directories = psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesScope(module));
          }
        }
        else {
          directories = psiPackage.getDirectories(GlobalSearchScope.notScope(GlobalSearchScope.projectScope(project)));
        }
      }
    }

    if (directories == null || directories.length == 0) {
      directories = psiPackage.getDirectories();
    }
    return directories;
  }

  @Override
  public boolean packagePrefixExists(@NotNull PsiPackage psiPackage) {
    return PackagePrefixElementFinder.getInstance(psiPackage.getProject()).packagePrefixExists(psiPackage.getQualifiedName());
  }

  @NotNull
  @Override
  public Object[] getDirectoryCachedValueDependencies(@NotNull PsiPackage psiPackage) {
    return new Object[] { PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT, ProjectRootManager.getInstance(psiPackage.getProject()) };
  }
}
