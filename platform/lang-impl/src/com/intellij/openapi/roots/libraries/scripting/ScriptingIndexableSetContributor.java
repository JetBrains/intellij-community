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
package com.intellij.openapi.roots.libraries.scripting;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexableSetContributor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public abstract class ScriptingIndexableSetContributor extends IndexableSetContributor {

  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@Nullable Project project) {
    return getLibraryFiles(project);
  }

  @NotNull
  public Set<VirtualFile> getLibraryFiles(Project project) {
    final THashSet<VirtualFile> libFiles = new THashSet<VirtualFile>();
    LibraryType libType = getLibraryType();
    if (project != null) {
      LibraryTable libTable = ScriptingLibraryManager.getLibraryTable(project, ScriptingLibraryManager.LibraryLevel.GLOBAL);
      if (libTable != null) {
        for (Library lib : libTable.getLibraries()) {
          if (lib instanceof LibraryEx && libType.equals(((LibraryEx)lib).getType())) {
            libFiles.addAll(Arrays.asList(lib.getFiles(OrderRootType.SOURCES)));
            libFiles.addAll(Arrays.asList(lib.getFiles(OrderRootType.CLASSES)));
          }
        }
      }
    }
    return libFiles;
  }

  @Override
  public final Set<VirtualFile> getAdditionalRootsToIndex() {
    return getPredefinedFilesToIndex();
  }

  public abstract Set<VirtualFile> getPredefinedFilesToIndex();

  public abstract Key<String> getIndexKey();

  public abstract LibraryType getLibraryType();
}
