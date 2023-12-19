// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.impl.search.JavaVersionBasedScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaResolveScopeProvider extends ResolveScopeProvider {
  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    // For java only! For other languages resolve may be implemented with different rules, requiring larger scope.
    FileType type = file.getFileType();
    if (type instanceof LanguageFileType langType && langType.getLanguage() == JavaLanguage.INSTANCE) {
      ProjectFileIndex index = project.isDefault() ? null : ProjectRootManager.getInstance(project).getFileIndex();
      if (index == null) {
        return GlobalSearchScope.fileScope(project, file);
      }
      if (index.isInContent(file) && !index.isInSource(file)) {
        // Limited resolve scope for all java files in module content, but not under source roots.
        // For example, java files from test data.
        // There is still a possibility to modify this scope choice with the ResolveScopeEnlarger.
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null || !JavaHighlightUtil.isJavaHashBangScript(psi)) {
          return GlobalSearchScope.fileScope(project, file);
        }
      }
      Module module = index.getModuleForFile(file);
      if (module != null) {
        // Specify preferred language level to support multi-release Jars
        LanguageLevel level = LanguageLevelUtil.getEffectiveLanguageLevel(module);
        boolean includeTests = TestSourcesFilter.isTestSources(file, project);
        GlobalSearchScope baseScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
        return new JavaVersionBasedScope(project, baseScope, level);
      }
      if (file instanceof LightVirtualFile) {
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi != null) {
          PsiFile originalFile = psi.getOriginalFile();
          if (originalFile != psi && originalFile.getFileType().equals(JavaClassFileType.INSTANCE)) {
            return getClassFileScope(originalFile.getVirtualFile(), project);
          }
        }
      }
    }
    else if (type.equals(JavaClassFileType.INSTANCE)) {
      return getClassFileScope(file, project);
    }
    return null;
  }

  @Nullable
  private static JavaVersionBasedScope getClassFileScope(@NotNull VirtualFile file, @NotNull Project project) {
    ProjectFileIndex index = project.isDefault() ? null : ProjectRootManager.getInstance(project).getFileIndex();
    LanguageLevel level = JavaMultiReleaseUtil.getVersion(file);
    if (level != null && index != null) {
      GlobalSearchScope baseScope = LibraryScopeCache.getInstance(project).getLibraryScope(index.getOrderEntriesForFile(file));
      return new JavaVersionBasedScope(project, baseScope, level);
    }
    return null;
  }
}
