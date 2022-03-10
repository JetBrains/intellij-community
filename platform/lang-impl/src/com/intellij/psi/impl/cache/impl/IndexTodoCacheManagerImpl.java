// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
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
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.function.IntPredicate;

public class IndexTodoCacheManagerImpl implements TodoCacheManager {
  private static final Logger LOG = Logger.getInstance(IndexTodoCacheManagerImpl.class);

  private final Project myProject;

  public IndexTodoCacheManagerImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public PsiFile @NotNull [] getFilesWithTodoItems() {
    HashSet<PsiFile> files = new HashSet<>();
    processFilesWithTodoItems(new CommonProcessors.CollectProcessor<>(files));
    return PsiUtilCore.toPsiFileArray(files);
  }

  @Override
  public boolean processFilesWithTodoItems(@NotNull Processor<? super PsiFile> processor) {
    if (myProject.isDefault()) return true;
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    ConcurrentBitSet idSet = ConcurrentBitSet.create();

    ManagingFS fs = ManagingFS.getInstance();
    PsiManager psiManager = PsiManager.getInstance(myProject);
    IntPredicate consumer = fileId -> {
      VirtualFile file = fs.findFileById(fileId);
      if (file == null || !file.isValid() || !scope.contains(file) || !TodoIndexers.belongsToProject(myProject, file)) return true;
      PsiFile psiFile = psiManager.findFile(file);
      return psiFile == null || processor.process(psiFile);
    };
    DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
      FileBasedIndex.getInstance().processAllKeys(TodoIndex.NAME, fileId -> {
        idSet.set(fileId);
        return true;
      }, scope, null);
    });
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    fileDocumentManager.processUnsavedDocuments(document -> {
      VirtualFile file = fileDocumentManager.getFile(document);
      if (file instanceof VirtualFileWithId) {
        idSet.clear(((VirtualFileWithId)file).getId());
      }
      return true;
    });
    for (int fileId = idSet.nextSetBit(0); fileId > 0; fileId = idSet.nextSetBit(fileId + 1)) {
      if (IndexingStamp.isFileIndexedStateCurrent(fileId, TodoIndex.NAME) != FileIndexingState.UP_TO_DATE) {
        idSet.clear(fileId);
      }
      else if (!consumer.test(fileId)) return false;
    }
    IdFilter filter = ((FileBasedIndexEx)FileBasedIndex.getInstance()).extractIdFilter(scope, myProject);
    if (!FileBasedIndexScanUtil.doProcessAllKeys(TodoIndex.NAME, fileId -> idSet.set(fileId) || consumer.test(fileId), scope, filter)) {
      return false;
    }
    return true;
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
    if (file instanceof VirtualFileWindow) return -1;

    Map<TodoIndexEntry, Integer> data = getTodoMap(myProject, file);
    if (data == null || data.isEmpty()) return 0;

    int result = 0;
    for (IndexPattern pattern : indexPatterns) {
      result += data.getOrDefault(new TodoIndexEntry(pattern.getPatternString(), pattern.isCaseSensitive()), 0);
    }
    return result;
  }

  public static @Nullable Map<TodoIndexEntry, Integer> getTodoMap(@NotNull Project project, @NotNull VirtualFile file) {
    if (project.isDefault()) return null;
    if (file instanceof VirtualFileWindow) return null;
    return file instanceof LightVirtualFile ? calcTodoMap(project, (LightVirtualFile)file) :
           TodoIndexers.belongsToProject(project, file) ? getTodoMapFromIndex(project, file) : null;
  }

  private static @Nullable Map<TodoIndexEntry, Integer> calcTodoMap(@NotNull Project project, @NotNull LightVirtualFile file) {
    CharSequence content = file.getContent();
    if (StringUtil.isEmpty(content)) return null;
    TodoIndex extension = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtension(TodoIndex.class);
    if (extension == null) return null;
    FileContent fc = FileContentImpl.createByText(file, content, project);
    Map<Integer, Map<TodoIndexEntry, Integer>> data = extension.getIndexer().map(fc);
    return ContainerUtil.getFirstItem(data.values());
  }

  private static @Nullable Map<TodoIndexEntry, Integer> getTodoMapFromIndex(@NotNull Project project, @NotNull VirtualFile file) {
    Map<Integer, Map<TodoIndexEntry, Integer>> map = FileBasedIndexScanUtil.getIndexData(TodoIndex.NAME, project, file);
    return map == null ? null : ContainerUtil.getFirstItem(map.values());
  }
}
