// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.ArrayList;
import java.util.List;


public class PsiPackageImplementationHelperImpl extends PsiPackageImplementationHelper {
  @NotNull
  @Override
  public GlobalSearchScope adjustAllScope(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope globalSearchScope) {
    return NonClasspathClassFinder.addNonClasspathScope(psiPackage.getProject(), globalSearchScope);
  }

  @Override
  public VirtualFile @NotNull [] occursInPackagePrefixes(@NotNull PsiPackage psiPackage) {
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
          if (PsiNameHelper.isSubpackageOf(packagePrefix, oldQualifiedName)) {
            sourceFolder.setPackagePrefix(newQualifiedName + packagePrefix.substring(oldQualifiedName.length()));
            anyChange = true;
          }
        }
      }
      if (anyChange) {
        modelsToCommit.add(rootModel);
      }
      else {
        rootModel.dispose();
      }
    }

    if (!modelsToCommit.isEmpty()) {
      ModifiableModelCommitter
        .multiCommit(modelsToCommit, ModuleManager.getInstance(modelsToCommit.get(0).getProject()).getModifiableModel());
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void navigate(@NotNull final PsiPackage psiPackage, final boolean requestFocus) {
    final Project project = psiPackage.getProject();
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (window != null) {
      window.activate(null);
    }
    final ProjectView projectView = ProjectView.getInstance(project);
    PsiDirectory[] directories = suggestMostAppropriateDirectories(psiPackage);
    if (directories.length == 0) return;
    projectView.select(directories[0], directories[0].getVirtualFile(), requestFocus);
  }

  @VisibleForTesting
  public static PsiDirectory @NotNull [] suggestMostAppropriateDirectories(@NotNull PsiPackage psiPackage) {
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
          if (virtualFile != null) {
            if (ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(virtualFile)) {
              directories = psiPackage.getDirectories(GlobalSearchScope.moduleTestsWithDependentsScope(module));
            }

            if (directories == null || directories.length == 0) {
              VirtualFile contentRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(virtualFile);
              if (contentRootForFile != null) {
                directories = psiPackage.getDirectories(GlobalSearchScopes.directoriesScope(project, true, contentRootForFile));
              }
            }
          }

          if (directories == null || directories.length == 0) {
            directories = psiPackage.getDirectories(GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module));
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

  @Override
  public Object @NotNull [] getDirectoryCachedValueDependencies(@NotNull PsiPackage psiPackage) {
    return new Object[] {PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(psiPackage.getProject()) };
  }
}
