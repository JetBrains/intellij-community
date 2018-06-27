// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deadCode.jvm;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.reference.RefUtil;
import com.intellij.compiler.PwaService;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;

public class JvmDeadCodeSearcher {
  static boolean isDirectlyUsed(PsiElement psiElement, JvmElement jvmElement) {
    // is implicitly used in terms of sources
    if (RefUtil.isImplicitRead(psiElement)) {
      return true;
    }

    // search bytecode references
    if (PwaService.getInstance(psiElement.getProject()).isBytecodeUsed(jvmElement)) {
      return true;
    }

    // search implicit usages in terms of bytecode
    PsiReference anyRef = ReferencesSearch.search(psiElement, generateNonJvmScope(psiElement.getProject())).findFirst();
    return anyRef != null;
  }

  private static GlobalSearchScope generateNonJvmScope(Project project) {
    return ProjectScope.getAllScope(project).intersectWith(new GlobalSearchScope() {
      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return true;
      }

      @Override
      public boolean isSearchInLibraries() {
        return true;
      }

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return isSupportedFileType(file);
      }
    });
  }

  private static boolean isSupportedFileType(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getFileType() == StdFileTypes.JAVA) return true;
    final String extension = virtualFile.getExtension();
    if ("kt".equals(extension) || "groovy".equals(extension)) return true;
    return false;
  }
}
