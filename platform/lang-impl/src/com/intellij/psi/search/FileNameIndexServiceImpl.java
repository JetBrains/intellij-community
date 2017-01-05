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
package com.intellij.psi.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;

public class FileNameIndexServiceImpl implements FileNameIndexService {
  @Override
  public Collection<VirtualFile> getVirtualFilesByName(Project project, String name, GlobalSearchScope scope, IdFilter filter) {
    final Set<VirtualFile> files = new THashSet<VirtualFile>();
    FileBasedIndex.getInstance().processValues(FilenameIndexImpl.NAME, name, null, (file, value) -> {
      files.add(file);
      return true;
    }, scope, filter);
    return files;
  }

  @Override
  public void processAllFileNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter) {
    final FileBasedIndex index = FileBasedIndex.getInstance();
    index.processAllKeys(FilenameIndexImpl.NAME, processor, scope, filter);
  }

  @Override
  public Collection<VirtualFile> getFilesWithFileType(FileType fileType, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(FileTypeIndexImpl.NAME, fileType, scope);
  }

  @Override
  public boolean processFilesWithFileType(FileType fileType, Processor<VirtualFile> processor, GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().processValues(
      FileTypeIndexImpl.NAME,
      fileType,
      null,
      (file, value) -> processor.process(file), scope);
  }
}
