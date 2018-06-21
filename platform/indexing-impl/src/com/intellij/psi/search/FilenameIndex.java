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

package com.intellij.psi.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class FilenameIndex {

  /**
   * @deprecated Not to be used.
   */
  @NonNls @Deprecated public static final ID<String, Void> NAME = ID.create("FilenameIndex");

  @NotNull
  public static String[] getAllFilenames(@Nullable Project project) {
    Set<String> names = new THashSet<>();
    getService().processAllFileNames((String s) -> {
      names.add(s);
      return true;
    }, project == null ? new EverythingGlobalScope() : GlobalSearchScope.allScope(project), null);
    return ArrayUtil.toStringArray(names);
  }

  public static void processAllFileNames(@NotNull Processor<String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    getService().processAllFileNames(processor, scope, filter);
  }

  @NotNull
  public static Collection<VirtualFile> getVirtualFilesByName(final Project project, @NotNull String name, @NotNull GlobalSearchScope scope) {
    return getService().getVirtualFilesByName(project, name, scope, null);
  }

  @NotNull
  public static Collection<VirtualFile> getVirtualFilesByName(final Project project,
                                                              @NotNull String name,
                                                              boolean caseSensitively,
                                                              @NotNull GlobalSearchScope scope) {
    if (caseSensitively) return getVirtualFilesByName(project, name, scope);
    return getVirtualFilesByNameIgnoringCase(name, scope, project, null);
  }

  @NotNull
  public static PsiFile[] getFilesByName(@NotNull Project project, @NotNull String name, @NotNull GlobalSearchScope scope) {
    return (PsiFile[])getFilesByName(project, name, scope, false);
  }

  public static boolean processFilesByName(@NotNull final String name,
                                           boolean directories,
                                           @NotNull Processor<? super PsiFileSystemItem> processor,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Project project,
                                           @Nullable IdFilter idFilter) {
    return processFilesByName(name, directories, true, processor, scope, project, idFilter);
  }

  public static boolean processFilesByName(@NotNull final String name,
                                           boolean directories,
                                           boolean caseSensitively,
                                           @NotNull Processor<? super PsiFileSystemItem> processor,
                                           @NotNull final GlobalSearchScope scope,
                                           @NotNull final Project project,
                                           @Nullable IdFilter idFilter) {
    final Collection<VirtualFile> files;

    if (caseSensitively) {
      files = getService().getVirtualFilesByName(project, name, scope, idFilter);
    }
    else {
      files = getVirtualFilesByNameIgnoringCase(name, scope, project, idFilter);
    }

    if (files.isEmpty()) return false;
    PsiManager psiManager = PsiManager.getInstance(project);
    int processedFiles = 0;

    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      if (!directories && !file.isDirectory()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          if(!processor.process(psiFile)) return true;
          ++processedFiles;
        }
      } else if (directories && file.isDirectory()) {
        PsiDirectory dir = psiManager.findDirectory(file);
        if (dir != null) {
          if(!processor.process(dir)) return true;
          ++processedFiles;
        }
      }
    }
    return processedFiles > 0;
  }

  @NotNull
  private static Set<VirtualFile> getVirtualFilesByNameIgnoringCase(@NotNull final String name,
                                                                    @NotNull final GlobalSearchScope scope,
                                                                    @NotNull Project project,
                                                                    @Nullable final IdFilter idFilter) {
    final Set<String> keys = new THashSet<>();
    FileNameIndexService fileNameIndexService = getService();
    fileNameIndexService.processAllFileNames(value -> {
      if (name.equalsIgnoreCase(value)) {
        keys.add(value);
      }
      return true;
    }, scope, idFilter);

    // values accessed outside of processAllKeys
    final Set<VirtualFile> files = new THashSet<>();
    for (String each : keys) {
      files.addAll(fileNameIndexService.getVirtualFilesByName(project, each, scope, idFilter));
    }
    return files;
  }

  @NotNull
  public static PsiFileSystemItem[] getFilesByName(@NotNull Project project,
                                                   @NotNull String name,
                                                   @NotNull final GlobalSearchScope scope,
                                                   boolean includeDirs) {
    SmartList<PsiFileSystemItem> result = new SmartList<>();
    Processor<PsiFileSystemItem> processor = Processors.cancelableCollectProcessor(result);
    processFilesByName(name, includeDirs, processor, scope, project, null);

    if (includeDirs) {
      return ArrayUtil.toObjectArray(result, PsiFileSystemItem.class);
    }
    //noinspection SuspiciousToArrayCall
    return result.toArray(PsiFile.EMPTY_ARRAY);
  }

  /**
   * Returns all files in the project by extension
   * @author Konstantin Bulenkov
   *
   * @param project current project
   * @param ext file extension without leading dot e.q. "txt", "wsdl"
   * @return all files with provided extension
   */
  @NotNull
  public static Collection<VirtualFile> getAllFilesByExt(@NotNull Project project, @NotNull String ext) {
    return getAllFilesByExt(project, ext, GlobalSearchScope.allScope(project));
  }

  @NotNull
  public static Collection<VirtualFile> getAllFilesByExt(@NotNull Project project, @NotNull String ext, @NotNull GlobalSearchScope searchScope) {
    int len = ext.length();

    if (len == 0) return Collections.emptyList();

    ext = "." + ext;
    len++;

    final List<VirtualFile> files = new ArrayList<>();
    for (String name : getAllFilenames(project)) {
      final int length = name.length();
      if (length > len && name.substring(length - len).equalsIgnoreCase(ext)) {
        files.addAll(getVirtualFilesByName(project, name, searchScope));
      }
    }
    return files;
  }

  static FileNameIndexService getService() {
    return ServiceManager.getService(FileNameIndexService.class);
  }
}
