/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

class EmptyCacheManager implements CacheManager {
  public void initialize() {
  }

  public void dispose() {
  }

  @NotNull
  public CacheUpdater[] getCacheUpdaters() {
    return new CacheUpdater[0];
  }

  @NotNull
  public PsiFile[] getFilesWithWord(@NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitive) {
    return PsiFile.EMPTY_ARRAY;
  }

  public boolean processFilesWithWord(@NotNull Processor<PsiFile> processor, @NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope,
                                      final boolean caseSensitively) {
    return true;
  }

  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    return PsiFile.EMPTY_ARRAY;
  }

  public int getTodoCount(@NotNull VirtualFile file, final IndexPatternProvider patternProvider) {
    return 0;
  }

  public int getTodoCount(@NotNull VirtualFile file, IndexPattern pattern) {
    return 0;
  }

  public void addOrInvalidateFile(@NotNull VirtualFile file) {
  }
}