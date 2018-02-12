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
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author max
 */
public interface JavaFileManager {
  class SERVICE {
    private SERVICE() { }

    /** @deprecated use {@link JavaFileManager#getInstance(Project)} (to be removed in IDEA 2018) */
    public static JavaFileManager getInstance(@NotNull Project project) {
      return JavaFileManager.getInstance(project);
    }
  }

  static JavaFileManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JavaFileManager.class);
  }

  @Nullable
  PsiPackage findPackage(@NotNull String packageName);

  @Nullable
  PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope);

  @NotNull
  PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope);

  @NotNull
  Collection<String> getNonTrivialPackagePrefixes();

  @NotNull
  Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope);
}