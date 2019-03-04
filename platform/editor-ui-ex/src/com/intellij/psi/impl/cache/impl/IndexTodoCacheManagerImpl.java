// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

  public IndexTodoCacheManagerImpl(@NotNull Project project) {
    myProject = project;
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
      PsiManager psiManager = PsiManager.getInstance(myProject);
      ApplicationManager.getApplication().runReadAction(() -> {
        for (VirtualFile file : files) {
          if (projectFileIndex.isInContent(file)) {
            final PsiFile psiFile = psiManager.findFile(file);
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
