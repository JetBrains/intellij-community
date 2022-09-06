/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LibraryScopeBase extends GlobalSearchScope {
  private final Object2IntMap<VirtualFile> myEntries; // Maps each classpath root to its position in the classpath.
  protected final ProjectFileIndex myIndex;

  public LibraryScopeBase(Project project, VirtualFile[] classes, VirtualFile[] sources) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myEntries = new Object2IntOpenHashMap<>(classes.length + sources.length);
    myEntries.defaultReturnValue(Integer.MAX_VALUE);
    for (VirtualFile file : classes) {
      myEntries.putIfAbsent(file, myEntries.size());
    }
    for (VirtualFile file : sources)  {
      myEntries.putIfAbsent(file, myEntries.size());
    }
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myEntries.containsKey(getFileRoot(file));
  }

  @Nullable
  protected VirtualFile getFileRoot(@NotNull VirtualFile file) {
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInLibrarySource(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    int pos1 = myEntries.getInt(getFileRoot(file1));
    int pos2 = myEntries.getInt(getFileRoot(file2));
    return Integer.compare(pos2, pos1);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LibraryScopeBase)) return false;

    return myEntries.keySet().equals(((LibraryScopeBase)o).myEntries.keySet());
  }

  @Override
  public int calcHashCode() {
    return myEntries.keySet().hashCode();
  }
}
