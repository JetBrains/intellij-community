// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.cache.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.cache.TodoCacheManager;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class IndexTodoCacheManagerImpl implements TodoCacheManager {
  private final Project myProject;

  public IndexTodoCacheManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public PsiFile @NotNull [] getFilesWithTodoItems() {
    if (myProject.isDefault()) {
      return PsiFile.EMPTY_ARRAY;
    }
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final Set<PsiFile> allFiles = new HashSet<>();

    fileBasedIndex.ignoreDumbMode(() -> {
      for (IndexPattern indexPattern : IndexPatternUtil.getIndexPatterns()) {
        final Collection<VirtualFile> files = fileBasedIndex.getContainingFiles(
          TodoIndex.NAME,
          new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), GlobalSearchScope.allScope(myProject));
        PsiManager psiManager = PsiManager.getInstance(myProject);
        for (VirtualFile file : files) {
          ReadAction.run(() -> {
            if (file.isValid() && TodoIndexers.belongsToProject(myProject, file)) {
              ContainerUtil.addIfNotNull(allFiles, psiManager.findFile(file));
            }
          });
        }
      }
    }, myProject, DumbModeAccessType.RELIABLE_DATA_ONLY);

    return allFiles.isEmpty() ? PsiFile.EMPTY_ARRAY : PsiUtilCore.toPsiFileArray(allFiles);
  }

  @Override
  public int getTodoCount(@NotNull final VirtualFile file, @NotNull final IndexPatternProvider patternProvider) {
    if (myProject.isDefault() || !TodoIndexers.belongsToProject(myProject, file)) {
      return 0;
    }
    if (file instanceof VirtualFileWindow) return -1;
    return fetchCount(file, patternProvider.getIndexPatterns());
  }

  @Override
  public int getTodoCount(@NotNull final VirtualFile file, @NotNull final IndexPattern pattern) {
    if (myProject.isDefault() || !TodoIndexers.belongsToProject(myProject, file)) {
      return 0;
    }
    if (file instanceof VirtualFileWindow) return -1;
    return fetchCount(file, pattern);
  }

  private int fetchCount(@NotNull VirtualFile file, IndexPattern @NotNull ... indexPatterns) {
    final int[] count = {0};
    FileBasedIndex.getInstance().ignoreDumbMode(() -> {
      Map<TodoIndexEntry, Integer> data = FileBasedIndex.getInstance().getFileData(TodoIndex.NAME, file, myProject);
      for (IndexPattern indexPattern : indexPatterns) {
        count[0] += data.getOrDefault(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), 0);
      }
    }, myProject, DumbModeAccessType.RELIABLE_DATA_ONLY);
    return count[0];
  }
}
