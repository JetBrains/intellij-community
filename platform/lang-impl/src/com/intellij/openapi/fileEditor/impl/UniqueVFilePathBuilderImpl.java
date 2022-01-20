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
import com.intellij.util.containers.ContainerUtil;
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

    final String fileName = getName(file);
    UniqueNameBuilder<VirtualFile> uniqueNameBuilderForShortName = valueMap.get(fileName);

    if (uniqueNameBuilderForShortName == null) {
      UniqueNameBuilder<VirtualFile> builder = filesWithTheSameName(fileName, project, skipNonOpenedFiles, scope);
      valueMap.put(fileName, builder != null ? builder : ourEmptyBuilder);
      uniqueNameBuilderForShortName = builder;
    }
    else if (uniqueNameBuilderForShortName == ourEmptyBuilder) {
      uniqueNameBuilderForShortName = null;
    }

    if (uniqueNameBuilderForShortName != null && uniqueNameBuilderForShortName.contains(file)) {
      return uniqueNameBuilderForShortName;
    }

    return null;
  }

  @NotNull
  private static ModificationTracker getFilenameIndexModificationTracker(@NotNull Project project) {
    if (Registry.is("indexing.filename.over.vfs")) {
      return () -> ManagingFS.getInstance().getModificationCount();
    }
    return () -> disableIndexUpToDateCheckInEdt(() -> FileBasedIndex.getInstance().getIndexModificationStamp(FilenameIndex.NAME, project)); //
  }

  @Nullable
  private static UniqueNameBuilder<VirtualFile> filesWithTheSameName(@NotNull String fileName,
                                                                     @NotNull Project project,
                                                                     boolean skipNonOpenedFiles,
                                                                     @NotNull GlobalSearchScope scope) {
    boolean useIndex = !skipNonOpenedFiles && !LightEdit.owns(project);
    Collection<VirtualFile> filesWithSameName = useIndex ? getFilesByNameFromIndex(fileName, project, scope) : Collections.emptySet();
    Set<VirtualFile> setOfFilesWithTheSameName = new HashSet<>(filesWithSameName);
    // add open files out of project scope
    for (VirtualFile openFile : FileEditorManager.getInstance(project).getOpenFiles()) {
      if (getName(openFile).equals(fileName)) {
        setOfFilesWithTheSameName.add(openFile);
      }
    }
    if (!skipNonOpenedFiles) {
      for (VirtualFile recentlyEditedFile : EditorHistoryManager.getInstance(project).getFileList()) {
        if (getName(recentlyEditedFile).equals(fileName)) {
          setOfFilesWithTheSameName.add(recentlyEditedFile);
        }
      }
    }

    filesWithSameName = setOfFilesWithTheSameName;

    if (filesWithSameName.size() > 1) {
      String path = project.getBasePath();
      path = path == null ? "" : FileUtil.toSystemIndependentName(path);
      UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<>(path, File.separator, 25);
      for (VirtualFile virtualFile : filesWithSameName) {
        String presentablePath = virtualFile instanceof VirtualFilePathWrapper ?
                                 ((VirtualFilePathWrapper)virtualFile).getPresentablePath() : virtualFile.getPath();
        builder.addPath(virtualFile, presentablePath);
      }
      return builder;
    }

    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getFilesByNameFromIndex(@NotNull String fileName, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    ThrowableComputable<Collection<VirtualFile>, RuntimeException> query =
      () -> FilenameIndex.getVirtualFilesByName(fileName, scope);
    if (DumbService.isDumb(project)) {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(query);
    }
    // get data as is
    Collection<VirtualFile> rawDataFromIndex = disableIndexUpToDateCheckInEdt(query);
    // filter only suitable files, we can miss some files but it's ok for presentation reasons
    return ContainerUtil.filter(rawDataFromIndex, f -> fileName.equals(getName(f)));
  }

  private static <T,E extends Throwable> T disableIndexUpToDateCheckInEdt(@NotNull ThrowableComputable<T, E> computable) throws E {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return ApplicationManager.getApplication().isDispatchThread()
           ? FileBasedIndexImpl.disableUpToDateCheckIn(computable)
           : computable.compute();
  }
}