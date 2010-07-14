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

package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class CompositeCacheManager implements CacheManager{
  private final List<CacheManager> myManagers = new ArrayList<CacheManager>();

  public void addCacheManager(CacheManager manager) {
    myManagers.add(manager);
  }

  public void initialize() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.initialize();
    }
  }

  public void dispose() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.dispose();
    }
  }

  @NotNull
  public CacheUpdater[] getCacheUpdaters() {
    List<CacheUpdater> updaters = new ArrayList<CacheUpdater>();
    for (CacheManager cacheManager : myManagers) {
      ContainerUtil.addAll(updaters, cacheManager.getCacheUpdaters());
    }
    return updaters.toArray(new CacheUpdater[updaters.size()]);
  }

  @NotNull
  public PsiFile[] getFilesWithWord(@NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively) {
    CommonProcessors.CollectProcessor<PsiFile> processor = new CommonProcessors.CollectProcessor<PsiFile>();
    processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively);
    return processor.getResults().isEmpty() ? PsiFile.EMPTY_ARRAY : processor.toArray(new PsiFile[processor.getResults().size()]);
  }

  public boolean processFilesWithWord(@NotNull Processor<PsiFile> processor, @NotNull String word, short occurenceMask, @NotNull GlobalSearchScope scope, final boolean caseSensitively) {
    for (CacheManager cacheManager : myManagers) {
      if (!cacheManager.processFilesWithWord(processor, word, occurenceMask, scope, caseSensitively)) return false;
    }
    return true;
  }

  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    List<PsiFile> files = null;
    for (CacheManager cacheManager : myManagers) {
      PsiFile[] items = cacheManager.getFilesWithTodoItems();
      if (items.length != 0 && files == null) {
        files = new ArrayList<PsiFile>();
        ContainerUtil.addAll(files, items);
      }
    }
    return files == null ? PsiFile.EMPTY_ARRAY : files.toArray(new PsiFile[files.size()]);
  }

  public int getTodoCount(@NotNull VirtualFile file, final IndexPatternProvider patternProvider) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      int todoCount = cacheManager.getTodoCount(file, patternProvider);
      if (todoCount == -1) return -1;
      count += todoCount;
    }
    return count;
  }

  public int getTodoCount(@NotNull VirtualFile file, IndexPattern pattern) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      int todoCount = cacheManager.getTodoCount(file, pattern);
      if (todoCount == -1) return -1;
      count += todoCount;
    }
    return count;
  }

  public void addOrInvalidateFile(@NotNull VirtualFile file) {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.addOrInvalidateFile(file);
    }
  }
}
