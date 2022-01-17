// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IndexTodoCacheManagerImpl implements TodoCacheManager {
  private static final Logger LOG = Logger.getInstance(IndexTodoCacheManagerImpl.class);

  private final Project myProject;

  public IndexTodoCacheManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public PsiFile @NotNull [] getFilesWithTodoItems() {
    if (myProject.isDefault()) {
      return PsiFile.EMPTY_ARRAY;
    }
    ManagingFS fs = ManagingFS.getInstance();
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    Set<PsiFile> allFiles = new HashSet<>();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      fileBasedIndex.processAllKeys(TodoIndex.NAME, fileId -> {
        VirtualFile file = fs.findFileById(fileId);
        if (file == null || !scope.contains(file)) return true;
        ReadAction.run(() -> {
          if (file.isValid() && TodoIndexers.belongsToProject(myProject, file)) {
            ContainerUtil.addIfNotNull(allFiles, psiManager.findFile(file));
          }
        });
        return true;
      }, scope, null);
    });

    return allFiles.isEmpty() ? PsiFile.EMPTY_ARRAY : PsiUtilCore.toPsiFileArray(allFiles);
  }

  @Override
  public int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPatternProvider patternProvider) {
    return getTodoCountImpl(file, patternProvider.getIndexPatterns());
  }

  @Override
  public int getTodoCount(@NotNull VirtualFile file, @NotNull IndexPattern pattern) {
    return getTodoCountImpl(file, pattern);
  }

  private int getTodoCountImpl(@NotNull VirtualFile file, IndexPattern @NotNull ... indexPatterns) {
    if (myProject.isDefault()) {
      return 0;
    }

    if (file instanceof VirtualFileWindow) {
      return -1;
    }

    if (file instanceof LightVirtualFile) {
      return calculateTodoCount((LightVirtualFile)file, indexPatterns);
    }

    if (!TodoIndexers.belongsToProject(myProject, file)) {
      return 0;
    }

    return fetchTodoCountFromIndex(file, indexPatterns);
  }

  private int calculateTodoCount(@NotNull LightVirtualFile file, IndexPattern @NotNull [] indexPatterns) {
    TodoIndex extension = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(TodoIndex.class);
    if (extension == null) return 0;

    try {
      FileContent fc = FileContentImpl.createByFile(file, myProject);
      Map<Integer, Map<TodoIndexEntry, Integer>> data = extension.getIndexer().map(fc);
      return getTodoCountForInputData(ContainerUtil.getFirstItem(data.values()), indexPatterns);
    }
    catch (IOException e) {
      LOG.error(e);
      return 0;
    }
  }

  private int fetchTodoCountFromIndex(@NotNull VirtualFile file, IndexPattern @NotNull [] indexPatterns) {
    Ref<Map<TodoIndexEntry, Integer>> inputData = Ref.create();
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
      Map<TodoIndexEntry, Integer> data = FileBasedIndex.getInstance().getSingleEntryIndexData(TodoIndex.NAME, file, myProject);
      inputData.set(data);
    });
    return getTodoCountForInputData(inputData.get(), indexPatterns);
  }

  private static int getTodoCountForInputData(@Nullable Map<TodoIndexEntry, Integer> data, IndexPattern @NotNull [] indexPatterns) {
    if (data == null) return 0;

    return Arrays
      .stream(indexPatterns)
      .map(p -> new TodoIndexEntry(p.getPatternString(), p.isCaseSensitive()))
      .mapToInt(e -> data.getOrDefault(e, 0))
      .sum();
  }
}
