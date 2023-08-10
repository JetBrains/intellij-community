// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Limited resolve scope for all java files in module content, but not under source roots.
 * For example, java files from test data.
 * There is still a possibility to modify this scope choice with the ResolveScopeEnlarger.
 */
public final class JavaOutOfSourcesResolveScopeProvider extends ResolveScopeProvider {
  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @NotNull Project project) {
    // For java only! For other languages resolve may be implemented with different rules, requiring larger scope.
    FileType type = file.getFileType();
    if (type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() == JavaLanguage.INSTANCE) {
      ProjectFileIndex index = project.isDefault() ? null : ProjectRootManager.getInstance(project).getFileIndex();
      if (index == null) {
        return GlobalSearchScope.fileScope(project, file);
      }
      if (index.isInContent(file) && !index.isInSource(file)) {
        PsiFile psi = PsiManager.getInstance(project).findFile(file);
        if (psi == null || !JavaHighlightUtil.isJavaHashBangScript(psi)) {
          return GlobalSearchScope.fileScope(project, file);
        }
      }
      
    }
    return null;
  }
}
