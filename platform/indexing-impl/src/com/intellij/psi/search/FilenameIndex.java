/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class FilenameIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("FilenameIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  @NotNull
  @Override
  public ID<String,Void> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public boolean indexDirectories() {
    return true;
  }

  @Override
  public int getVersion() {
    return 1 + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 1 : 0);
  }

  public static String[] getAllFilenames(Project project) {
    final Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(NAME, project);
    return ArrayUtil.toStringArray(allKeys);
  }

  public static Collection<VirtualFile> getVirtualFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
  }

  public static PsiFile[] getFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    return (PsiFile[])getFilesByName(project, name, scope, false);
  }

  public static boolean processFilesByName(@NotNull final String name,
                                           boolean includeDirs,
                                           @NotNull Processor<? super PsiFileSystemItem> processor,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Project project,
                                           @Nullable IdFilter idFilter
                                       ) {
    final Set<VirtualFile> files = new THashSet<VirtualFile>();

    FileBasedIndex.getInstance().processValues(NAME, name, null, new FileBasedIndex.ValueProcessor<Void>() {
      @Override
      public boolean process(final VirtualFile file, final Void value) {
        files.add(file);
        return true;
      }
    }, scope, idFilter);

    if (files.isEmpty()) return false;
    PsiManager psiManager = PsiManager.getInstance(project);
    int processedFiles = 0;

    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      if (!includeDirs && !file.isDirectory()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          if(!processor.process(psiFile)) return true;
          ++processedFiles;
        }
      } else if (includeDirs && file.isDirectory()) {
        PsiDirectory dir = psiManager.findDirectory(file);
        if (dir != null) {
          if(!processor.process(dir)) return true;
          ++processedFiles;
        }
      }
    }
    return processedFiles > 0;
  }

  public static PsiFileSystemItem[] getFilesByName(final Project project,
                                         final String name,
                                         @NotNull final GlobalSearchScope scope,
                                         boolean includeDirs) {
    SmartList<PsiFileSystemItem> result = new SmartList<PsiFileSystemItem>();
    processFilesByName(name, includeDirs, new CommonProcessors.CollectProcessor<PsiFileSystemItem>(result), scope, project, null);

    if (includeDirs) {
      return ArrayUtil.toObjectArray(result, PsiFileSystemItem.class);
    }
    //noinspection SuspiciousToArrayCall
    return result.toArray(new PsiFile[result.size()]);
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    @Override
    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      return Collections.singletonMap(inputData.getFileName(), null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    @Override
    public boolean acceptInput(@NotNull final VirtualFile file) {
      return true;
    }
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

    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (String name : getAllFilenames(project)) {
      final int length = name.length();
      if (length > len && name.substring(length - len).equalsIgnoreCase(ext)) {
        files.addAll(getVirtualFilesByName(project, name, searchScope));
      }
    }
    return files;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  }
}
