/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexableSetContributor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public abstract class ScriptingIndexableSetContributor extends IndexableSetContributor {

  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex(@Nullable Project project) {
    final Set<VirtualFile> predefinedFiles = getPredefinedFilesToIndex();
    final THashSet<VirtualFile> filesToIndex = new THashSet<VirtualFile>();
    filesToIndex.addAll(predefinedFiles);
    if (project != null) {
      ScriptingLibraryManager manager = new ScriptingLibraryManager(project);
      LibraryTable libTable = manager.getLibraryTable();
      if (libTable != null) {
        for (Library lib : libTable.getLibraries()) {
          for (VirtualFile libFile : lib.getFiles(OrderRootType.CLASSES)) {
            libFile.putUserData(getIndexKey(), "");
            filesToIndex.add(libFile);
          }
        }
      }
      manager.disposeModel();
    }
    return filesToIndex;
  }

  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return getAdditionalRootsToIndex(null);
  }

  public abstract Set<VirtualFile> getPredefinedFilesToIndex();

  public abstract Key<String> getIndexKey();
}
