// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.analysis;

import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class AnalysisActionUtils {
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

  public static @Nullable AnalysisScope getInspectionScope(@NotNull DataContext dataContext, @NotNull Project project, Boolean acceptNonProjectDirectories) {
    AnalysisScope scope = getFileScopeFromInspectionView(dataContext);
    if (scope != null) return scope;
    scope = getInspectionScopeImpl(dataContext, project, acceptNonProjectDirectories);
    return scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
  }

  private static @NotNull AnalysisScope getInspectionScopeImpl(@NotNull DataContext dataContext, @NotNull Project project, Boolean acceptNonProjectDirectories) {
    // possible scopes: file, directory, package, project, module.
    Project projectContext = PlatformCoreDataKeys.PROJECT_CONTEXT.getData(dataContext);
    if (projectContext != null) {
      return new AnalysisScope(projectContext);
    }

    AnalysisScope analysisScope = AnalysisScopeUtil.KEY.getData(dataContext);
    if (analysisScope != null) {
      return analysisScope;
    }

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (psiFile != null && psiFile.getManager().isInProject(psiFile)) {
      VirtualFile file = psiFile.getVirtualFile();
      if (file != null && file.isValid() && file.getFileType() instanceof ArchiveFileType && acceptNonProjectDirectories) {
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file);
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
    if (virtualFiles != null) {
      // analyze on selection
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (virtualFiles.length == 1) {
        VirtualFile file = virtualFiles[0];
        PsiDirectory psiDirectory = file.isValid() ? PsiManager.getInstance(project).findDirectory(file) : null;
        if (psiDirectory != null && (acceptNonProjectDirectories || psiDirectory.getManager().isInProject(psiDirectory))) {
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

    return new AnalysisScope(project);
  }
}