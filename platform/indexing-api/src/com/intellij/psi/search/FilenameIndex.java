// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.Processors;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public final class FilenameIndex {
  @ApiStatus.Internal
  @NonNls
  public static final ID<String, Void> NAME = ID.create("FilenameIndex");

  public static String @NotNull [] getAllFilenames(@Nullable Project project) {
    Set<String> names = new HashSet<>();
    processAllFileNames((String s) -> {
      names.add(s);
      return true;
    }, project == null ? new EverythingGlobalScope() : GlobalSearchScope.allScope(project), null);
    return ArrayUtilRt.toStringArray(names);
  }

  public static void processAllFileNames(@NotNull Processor<? super String> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter filter) {
    FileBasedIndex.getInstance().processAllKeys(NAME, processor, scope, filter);
  }

  @NotNull
  public static Collection<VirtualFile> getVirtualFilesByName(final Project project, @NotNull String name, @NotNull GlobalSearchScope scope) {
    return getVirtualFilesByName(name, scope, null);
  }

  @NotNull
  public static Collection<VirtualFile> getVirtualFilesByName(final Project project,
                                                              @NotNull String name,
                                                              boolean caseSensitively,
                                                              @NotNull GlobalSearchScope scope) {
    if (caseSensitively) return getVirtualFilesByName(project, name, scope);
    return getVirtualFilesByNameIgnoringCase(name, scope, null);
  }

  public static PsiFile @NotNull [] getFilesByName(@NotNull Project project, @NotNull String name, @NotNull GlobalSearchScope scope) {
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
      files = getVirtualFilesByName(name, scope, idFilter);
    }
    else {
      files = getVirtualFilesByNameIgnoringCase(name, scope, idFilter);
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
                                                                    @Nullable final IdFilter idFilter) {
    Set<String> keys = CollectionFactory.createSmallMemoryFootprintSet();
    processAllFileNames(value -> {
      if (name.equalsIgnoreCase(value)) {
        keys.add(value);
      }
      return true;
    }, scope, idFilter);

    // values accessed outside of processAllKeys
    Set<VirtualFile> files = CollectionFactory.createSmallMemoryFootprintSet();
    for (String each : keys) {
      files.addAll(getVirtualFilesByName(each, scope, idFilter));
    }
    return files;
  }

  public static PsiFileSystemItem @NotNull [] getFilesByName(@NotNull Project project,
                                                             @NotNull String name,
                                                             @NotNull final GlobalSearchScope scope,
                                                             boolean directories) {
    SmartList<PsiFileSystemItem> result = new SmartList<>();
    Processor<PsiFileSystemItem> processor = Processors.cancelableCollectProcessor(result);
    processFilesByName(name, directories, processor, scope, project, null);

    if (directories) {
      return result.toArray(new PsiFileSystemItem[0]);
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

  @NotNull
  private static Collection<VirtualFile> getVirtualFilesByName(@NotNull String name,
                                                              @NotNull GlobalSearchScope scope,
                                                              IdFilter filter) {
    Set<VirtualFile> files = CollectionFactory.createSmallMemoryFootprintSet();
    FileBasedIndex.getInstance().processValues(NAME, name, null, (file, value) -> {
      files.add(file);
      return true;
    }, scope, filter);
    return files;
  }
}
