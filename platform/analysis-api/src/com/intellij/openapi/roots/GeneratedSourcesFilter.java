/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class GeneratedSourcesFilter {
  public static final ExtensionPointName<GeneratedSourcesFilter> EP_NAME = ExtensionPointName.create("com.intellij.generatedSourcesFilter");

  public abstract boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project);

  public static boolean isGeneratedSourceByAnyFilter(@NotNull VirtualFile file,
                                                     @NotNull Project project) {
    for (GeneratedSourcesFilter filter : EP_NAME.getExtensions()) {
      if (filter.isGeneratedSource(file, project)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isInProjectAndNotGenerated(@Nullable PsiElement element) {
    if (element == null || !element.isValid()) {
      return false;
    }
    final PsiManager manager = element.getManager();
    if (manager != null && manager.isInProject(element)) {
      return isNotGeneratedSource(element);
    }
    return false;
  }

  private static boolean isNotGeneratedSource(@Nullable PsiElement element) {
    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = null;
    if (file != null) {
      virtualFile = file.getViewProvider().getVirtualFile();
    }
    else if (element instanceof PsiFileSystemItem) {
      virtualFile = ((PsiFileSystemItem)element).getVirtualFile();
    }
    if (file != null && file.isPhysical() && virtualFile instanceof LightVirtualFile) return true;

    if (virtualFile != null) {
      return !isGeneratedSourceByAnyFilter(virtualFile, file.getProject());
    }
    return false;
  }
}
