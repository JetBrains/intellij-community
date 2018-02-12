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

package com.intellij.psi.impl.cache.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public class IndexTodoCacheManagerImpl implements TodoCacheManager {
  private final Project myProject;
  private final PsiManager myPsiManager;

  public IndexTodoCacheManagerImpl(PsiManager psiManager) {
    myPsiManager = psiManager;
    myProject = psiManager.getProject();
  }

  @Override
  @NotNull
  public PsiFile[] getFilesWithTodoItems() {
    if (myProject.isDefault()) {
      return PsiFile.EMPTY_ARRAY;
    }
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final Set<PsiFile> allFiles = new HashSet<>();
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
      final Collection<VirtualFile> files = fileBasedIndex.getContainingFiles(
        TodoIndex.NAME,
        new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), GlobalSearchScope.allScope(myProject));
      ApplicationManager.getApplication().runReadAction(() -> {
        for (VirtualFile file : files) {
          if (projectFileIndex.isInContent(file)) {
            final PsiFile psiFile = myPsiManager.findFile(file);
            if (psiFile != null) {
              allFiles.add(psiFile);
            }
          }
        }
      });
    }
    return allFiles.isEmpty() ? PsiFile.EMPTY_ARRAY : PsiUtilCore.toPsiFileArray(allFiles);
  }

  @Override
  public int getTodoCount(@NotNull final VirtualFile file, @NotNull final IndexPatternProvider patternProvider) {
    if (myProject.isDefault() || !ProjectFileIndex.getInstance(myProject).isInContent(file)) {
      return 0;
    }
    if (file instanceof VirtualFileWindow) return -1;
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    return Arrays.stream(patternProvider.getIndexPatterns()).mapToInt(indexPattern -> fetchCount(fileBasedIndex, file, indexPattern)).sum();
  }

  @Override
  public int getTodoCount(@NotNull final VirtualFile file, @NotNull final IndexPattern pattern) {
    if (myProject.isDefault() || !ProjectFileIndex.getInstance(myProject).isInContent(file)) {
      return 0;
    }
    if (file instanceof VirtualFileWindow) return -1;
    return fetchCount(FileBasedIndex.getInstance(), file, pattern);
  }

  private int fetchCount(@NotNull FileBasedIndex fileBasedIndex, @NotNull VirtualFile file, @NotNull IndexPattern indexPattern) {
    final int[] count = {0};
    fileBasedIndex.processValues(
      TodoIndex.NAME, new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), file,
      (file1, value) -> {
        count[0] += value.intValue();
        return true;
      }, GlobalSearchScope.fileScope(myProject, file));
    return count[0];
  }
}
