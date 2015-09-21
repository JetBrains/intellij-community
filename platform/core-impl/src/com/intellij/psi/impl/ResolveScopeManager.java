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
package com.intellij.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class ResolveScopeManager {
  @NotNull
  public abstract GlobalSearchScope getResolveScope(@NotNull PsiElement element);

  public abstract GlobalSearchScope getDefaultResolveScope(VirtualFile vFile);

  @NotNull
  public abstract GlobalSearchScope getUseScope(@NotNull PsiElement element);

  public static ResolveScopeManager getInstance(Project project) {
    return ServiceManager.getService(project, ResolveScopeManager.class);
  }

  @NotNull
  public static GlobalSearchScope getElementUseScope(@NotNull PsiElement element) {
    return getInstance(element.getProject()).getUseScope(element);
  }

  @NotNull
  public static GlobalSearchScope getElementResolveScope(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      return getInstance(file.getProject()).getResolveScope(file);
    }
    return getInstance(element.getProject()).getResolveScope(element);
  }
}
