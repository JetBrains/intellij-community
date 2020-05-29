/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.psi.impl.cache;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.Processor;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides low-level identifier search for a project.
 * Basically it uses {@link com.intellij.psi.impl.cache.impl.id.IdIndex} data,
 * so some custom language implementation may filter out some words on indexing stage {@link BaseFilterLexer}.
 *
 * Similar to {@link PsiSearchHelper} but doesn't operate with {@link PsiFile}-s.
 */
public interface CacheManager {

  class SERVICE {
    private SERVICE() {
    }

    /**
     * @deprecated use {@link CacheManager#getInstance(Project)}
     */
    @Deprecated
    public static CacheManager getInstance(Project project) {
      return ServiceManager.getService(project, CacheManager.class);
    }
  }

  @NotNull
  static CacheManager getInstance(Project project) {
    return ServiceManager.getService(project, CacheManager.class);
  }

  PsiFile @NotNull [] getFilesWithWord(@NotNull String word,
                                       short occurenceMask,
                                       @NotNull GlobalSearchScope scope,
                                       final boolean caseSensitively);

  VirtualFile @NotNull [] getVirtualFilesWithWord(@NotNull String word,
                                                  short occurenceMask,
                                                  @NotNull GlobalSearchScope scope,
                                                  final boolean caseSensitively);

  boolean processVirtualFilesWithAllWords(@NotNull Collection<String> words,
                                          short occurenceMask,
                                          @NotNull GlobalSearchScope scope,
                                          boolean caseSensitively,
                                          @NotNull Processor<? super VirtualFile> processor);

  boolean processFilesWithWord(@NotNull Processor<? super PsiFile> processor,
                               @NotNull String word,
                               @MagicConstant(flagsFromClass = UsageSearchContext.class) short occurenceMask,
                               @NotNull GlobalSearchScope scope,
                               final boolean caseSensitively);
}

