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
package com.intellij.psi.impl.file.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Limited resolve scope for all java files in module content, but not under source roots.
 * For example, java files from test data.
 * There is still a possibility to modify this scope choice with the ResolveScopeEnlarger.
 */
public class JavaOutOfSourcesResolveScopeProvider extends ResolveScopeProvider {
  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, Project project) {
    // For java only! For other languages resolve may be implemented with different rules, requiring larger scope.
    final FileType type = file.getFileType();
    if (type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() == JavaLanguage.INSTANCE) {
      ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
      if (index.isInContent(file) && !index.isInSource(file)) {
        return GlobalSearchScope.fileScope(project, file);
      }
    }
    return null;
  }
}
