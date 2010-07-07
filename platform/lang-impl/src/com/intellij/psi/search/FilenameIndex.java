/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class FilenameIndex extends ScalarIndexExtension<String> {
  @NonNls public static final ID<String, Void> NAME = ID.create("FilenameIndex");
  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final MyInputFilter myInputFilter = new MyInputFilter();
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  public ID<String,Void> getName() {
    return NAME;
  }

  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return myInputFilter;
  }

  public boolean dependsOnFileContent() {
    return false;
  }

  public int getVersion() {
    return 0;
  }

  public static String[] getAllFilenames(Project project) {
    final Collection<String> allKeys = FileBasedIndex.getInstance().getAllKeys(NAME, project);
    return ArrayUtil.toStringArray(allKeys);
  }

  public static Collection<VirtualFile> getVirtualFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
  }

  public static PsiFile[] getFilesByName(final Project project, final String name, final GlobalSearchScope scope) {
    final Collection<VirtualFile> files = FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope);
    if (files.isEmpty()) return PsiFile.EMPTY_ARRAY;
    List<PsiFile> result = new ArrayList<PsiFile>();
    for(VirtualFile file: files) {
      if (!file.isValid()) continue;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        result.add(psiFile);
      }
    }
    return result.toArray(new PsiFile[result.size()]);
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {
    @NotNull
    public Map<String, Void> map(final FileContent inputData) {
      return Collections.singletonMap(inputData.getFileName(), null);
    }
  }

  private static class MyInputFilter implements FileBasedIndex.InputFilter {
    public boolean acceptInput(final VirtualFile file) {
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
    int len = ext.length();

    if (len == 0) return Collections.emptyList();

    ext = "." + ext;
    len++;

    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (String name : getAllFilenames(project)) {
      final int length = name.length();
      if (length > len && name.substring(length - len).equalsIgnoreCase(ext)) {
        files.addAll(getVirtualFilesByName(project, name, GlobalSearchScope.allScope(project)));
      }
    }
    return files;
  }
}
