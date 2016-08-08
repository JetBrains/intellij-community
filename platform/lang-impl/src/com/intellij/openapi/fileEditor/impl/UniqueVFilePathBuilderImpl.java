/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePathWrapper;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentMap;

/**
 * @author yole
 */
public class UniqueVFilePathBuilderImpl extends UniqueVFilePathBuilder {
  @NotNull
  @Override
  public String getUniqueVirtualFilePath(Project project, VirtualFile file) {
    return getUniqueVirtualFilePath(project, file, false);
  }

  @NotNull
  @Override
  public String getUniqueVirtualFilePathWithinOpenedFileEditors(Project project, VirtualFile vFile) {
    return getUniqueVirtualFilePath(project, vFile, true);
  }

  private static final Key<CachedValue<ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>>
    ourShortNameBuilderCacheKey = Key.create("project's.short.file.name.builder");
  private static final Key<CachedValue<ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>>
    ourShortNameOpenedBuilderCacheKey = Key.create("project's.short.file.name.opened.builder");
  private static final UniqueNameBuilder<VirtualFile> ourEmptyBuilder = new UniqueNameBuilder<>(null, null, -1);

  private static String getUniqueVirtualFilePath(final Project project, VirtualFile file, final boolean skipNonOpenedFiles) {
    Key<CachedValue<ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>> key =
      skipNonOpenedFiles ?  ourShortNameOpenedBuilderCacheKey:ourShortNameBuilderCacheKey;
    CachedValue<ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>> data = project.getUserData(key);
    if (data == null) {
      project.putUserData(key, data = CachedValuesManager.getManager(project).createCachedValue(
        () -> new CachedValueProvider.Result<ConcurrentMap<String, UniqueNameBuilder<VirtualFile>>>(
          ContainerUtil.createConcurrentSoftValueMap(),
          PsiModificationTracker.MODIFICATION_COUNT,
          //ProjectRootModificationTracker.getInstance(project),
          //VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
          FileEditorManagerImpl.OPEN_FILE_SET_MODIFICATION_COUNT
        ), false));
    }

    final ConcurrentMap<String, UniqueNameBuilder<VirtualFile>> valueMap = data.getValue();
    final String fileName = file.getName();
    UniqueNameBuilder<VirtualFile> uniqueNameBuilderForShortName = valueMap.get(fileName);

    if (uniqueNameBuilderForShortName == null) {
      final UniqueNameBuilder<VirtualFile> builder = filesWithTheSameName(
        fileName,
        project,
        skipNonOpenedFiles,
        ProjectScope.getProjectScope(project)
      );
      valueMap.put(fileName, builder != null ? builder:ourEmptyBuilder);
      uniqueNameBuilderForShortName = builder;
    } else if (uniqueNameBuilderForShortName == ourEmptyBuilder) {
      uniqueNameBuilderForShortName = null;
    }

    if (uniqueNameBuilderForShortName != null && uniqueNameBuilderForShortName.contains(file)) {
      if (file instanceof VirtualFilePathWrapper) {
        return ((VirtualFilePathWrapper)file).getPresentablePath();
      }
      return uniqueNameBuilderForShortName.getShortPath(file);
    }
    return file.getName();
  }

  @Nullable
  private static UniqueNameBuilder<VirtualFile> filesWithTheSameName(String fileName, Project project,
                                                              boolean skipNonOpenedFiles,
                                                              GlobalSearchScope scope) {
    Collection<VirtualFile> filesWithSameName = skipNonOpenedFiles ? Collections.emptySet() :
                                                FilenameIndex.getVirtualFilesByName(project, fileName,
                                                                                    scope);
    THashSet<VirtualFile> setOfFilesWithTheSameName = new THashSet<>(filesWithSameName);
    // add open files out of project scope
    for(VirtualFile openFile: FileEditorManager.getInstance(project).getOpenFiles()) {
      if (openFile.getName().equals(fileName)) {
        setOfFilesWithTheSameName.add(openFile);
      }
    }
    if (!skipNonOpenedFiles) {
      for (VirtualFile recentlyEditedFile : EditorHistoryManager.getInstance(project).getFiles()) {
        if (recentlyEditedFile.getName().equals(fileName)) {
          setOfFilesWithTheSameName.add(recentlyEditedFile);
        }
      }
    }

    filesWithSameName = setOfFilesWithTheSameName;

    if (filesWithSameName.size() > 1) {
      String path = project.getBasePath();
      path = path == null ? "" : FileUtil.toSystemIndependentName(path);
      UniqueNameBuilder<VirtualFile> builder = new UniqueNameBuilder<>(path, File.separator, 25);
      for (VirtualFile virtualFile: filesWithSameName) {
        builder.addPath(virtualFile, virtualFile.getPath());
      }
      return builder;
    }
    return null;
  }
}
