// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


final class UniqueVFilePathBuilderImpl extends UniqueVFilePathBuilder {
  @NotNull
  @Override
  public String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile file, @NotNull GlobalSearchScope scope) {
    return getUniqueVirtualFilePath(project, file, false, scope);
  }

  @NotNull
  @Override
  public String getUniqueVirtualFilePath(@NotNull Project project, @NotNull VirtualFile vFile) {
    return getUniqueVirtualFilePath(project, vFile, GlobalSearchScope.projectScope(project));
  }

  @NotNull
  @Override
  public String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull Project project, @NotNull VirtualFile vFile) {
    return getUniqueVirtualFilePath(project, vFile, true, GlobalSearchScope.projectScope(project));
  }

  private static final Key<CachedValue<Map<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>>>>
    ourShortNameBuilderCacheKey = Key.create("project's.short.file.name.builder");
  private static final Key<CachedValue<Map<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>>>>
    ourShortNameOpenedBuilderCacheKey = Key.create("project's.short.file.name.opened.builder");
  private static final UniqueNameBuilder<VirtualFile> ourEmptyBuilder = new UniqueNameBuilder<>(null, null, -1);

  @NotNull
  private static String getName(@NotNull VirtualFile file) {
    return file instanceof VirtualFilePathWrapper ? file.getPresentableName() : file.getName();
  }

  private static String getUniqueVirtualFilePath(@NotNull Project project,
                                                 @NotNull VirtualFile file,
                                                 boolean skipNonOpenedFiles,
                                                 @NotNull GlobalSearchScope scope) {
    UniqueNameBuilder<VirtualFile> builder = getUniqueVirtualFileNameBuilder(project,
                                                                             file,
                                                                             skipNonOpenedFiles,
                                                                             scope);
    if (builder != null) {
      return builder.getShortPath(file);
    }
    return getName(file);
  }

  @Nullable
  private static UniqueNameBuilder<VirtualFile> getUniqueVirtualFileNameBuilder(@NotNull Project project,
                                                                                @NotNull VirtualFile file,
                                                                                boolean skipNonOpenedFiles,
                                                                                @NotNull GlobalSearchScope scope) {
    Key<CachedValue<Map<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>>>> key =
      skipNonOpenedFiles ? ourShortNameOpenedBuilderCacheKey : ourShortNameBuilderCacheKey;
    CachedValue<Map<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>>> data = project.getUserData(key);
    if (data == null) {
      project.putUserData(key, data = CachedValuesManager.getManager(project).createCachedValue(
        () -> new CachedValueProvider.Result<>(
          new ConcurrentHashMap<>(2),
          DumbService.getInstance(project),
          getFilenameIndexModificationTracker(project),
          FileEditorManagerImpl.OPEN_FILE_SET_MODIFICATION_COUNT
        ), false));
    }

    ConcurrentMap<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>> scope2ValueMap =
      (ConcurrentMap<GlobalSearchScope, Map<String, UniqueNameBuilder<VirtualFile>>>)data.getValue();
    Map<String, UniqueNameBuilder<VirtualFile>> valueMap = scope2ValueMap.get(scope);
    if (valueMap == null) {
      valueMap = ConcurrencyUtil.cacheOrGet(scope2ValueMap, scope, ContainerUtil.createConcurrentSoftValueMap());
    }

    String fileName = getName(file);
    UniqueNameBuilder<VirtualFile> builder = valueMap.get(fileName);

    if (builder == null) {
      createAndCacheBuilders(project, file, valueMap, skipNonOpenedFiles, scope);
      builder = ObjectUtils.nullizeIfDefaultValue(valueMap.get(fileName), ourEmptyBuilder);
    }
    else if (builder == ourEmptyBuilder) {
      builder = null;
    }

    if (builder != null && builder.contains(file)) {
      return builder;
    }

    return null;
  }

  @NotNull
  private static ModificationTracker getFilenameIndexModificationTracker(@NotNull Project project) {
    if (Registry.is("indexing.filename.over.vfs")) {
      return () -> ManagingFS.getInstance().getModificationCount();
    }
    return () -> disableIndexUpToDateCheckInEdt(() -> FileBasedIndex.getInstance().getIndexModificationStamp(FilenameIndex.NAME, project));
  }

  private static void createAndCacheBuilders(@NotNull Project project,
                                             @NotNull VirtualFile requiredFile,
                                             @NotNull Map<String, UniqueNameBuilder<VirtualFile>> valueMap,
                                             boolean skipNonOpenedFiles,
                                             @NotNull GlobalSearchScope scope) {
    boolean useIndex = !skipNonOpenedFiles && !LightEdit.owns(project);
    VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
    List<VirtualFile> recentFiles = EditorHistoryManager.getInstance(project).getFileList();

    MultiMap<String, VirtualFile> multiMap = MultiMap.createSet();
    if (useIndex) {
      Set<String> names = JBIterable.of(requiredFile).append(openFiles).append(recentFiles).map(o -> getName(o)).toSet();
      ThrowableComputable<Boolean, RuntimeException> query = () -> FilenameIndex.processFilesByNames(
        names, true, scope, null, file -> {
          String name = getName(file);
          if (names.contains(name)) { // not-up-to-date index check
            multiMap.putValue(name, file);
          }
          return true;
        });
      if (DumbService.isDumb(project)) {
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(query);
      }
      else {
        disableIndexUpToDateCheckInEdt(query);
      }
    }
    String requiredFileName = getName(requiredFile);
    for (VirtualFile file : JBIterable.of(openFiles).append(skipNonOpenedFiles ? Collections.emptyList() : recentFiles)) {
      if (getName(file).equals(requiredFileName)) {
        multiMap.putValue(requiredFileName, file);
      }
    }
    for (String fileName : multiMap.keySet()) {
      Collection<VirtualFile> files = multiMap.get(fileName);
      if (files.size() < 2) {
        valueMap.put(fileName, ourEmptyBuilder);
        continue;
      }
      String path = project.getBasePath();
      path = path == null ? "" : FileUtil.toSystemIndependentName(path);
      UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<>(path, File.separator, 25);
      for (VirtualFile virtualFile : files) {
        String presentablePath = virtualFile instanceof VirtualFilePathWrapper ?
                                 ((VirtualFilePathWrapper)virtualFile).getPresentablePath() : virtualFile.getPath();
        builder.addPath(virtualFile, presentablePath);
      }
      valueMap.put(fileName, builder);
    }
  }

  private static <T,E extends Throwable> T disableIndexUpToDateCheckInEdt(@NotNull ThrowableComputable<T, E> computable) throws E {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return ApplicationManager.getApplication().isDispatchThread()
           ? FileBasedIndexImpl.disableUpToDateCheckIn(computable)
           : computable.compute();
  }
}