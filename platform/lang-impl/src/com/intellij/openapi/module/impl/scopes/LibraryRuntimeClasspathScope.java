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

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final LinkedHashMap<VirtualFile, Integer> myEntries = new LinkedHashMap<VirtualFile, Integer>();

  public LibraryRuntimeClasspathScope(final Project project, final List<Module> modules) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Set<Sdk> processedSdk = new THashSet<Sdk>();
    final Set<Library> processedLibraries = new THashSet<Library>();

    ProjectRootManager.getInstance(project).orderEntries(modules).recursively()
      .process(new RootPolicy<LinkedHashMap<VirtualFile, Integer>>() {
        @Override
        public LinkedHashMap<VirtualFile, Integer> visitLibraryOrderEntry(final LibraryOrderEntry libraryOrderEntry,
                                                                          final LinkedHashMap<VirtualFile, Integer> value) {
          final Library library = libraryOrderEntry.getLibrary();
          if (library != null && processedLibraries.add(library)) {
            addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.CLASSES));
          }
          return value;
        }

        @Override
        public LinkedHashMap<VirtualFile, Integer> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                               final LinkedHashMap<VirtualFile, Integer> value) {
          addAll(value, moduleSourceOrderEntry.getFiles(OrderRootType.SOURCES));
          return value;
        }

        @Override
        public LinkedHashMap<VirtualFile, Integer> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry,
                                                                         LinkedHashMap<VirtualFile, Integer> value) {
          final Module depModule = moduleOrderEntry.getModule();
          if (depModule != null) {
            addAll(value, ModuleRootManager.getInstance(depModule).getSourceRoots());
          }
          return value;
        }

        @Override
        public LinkedHashMap<VirtualFile, Integer> visitJdkOrderEntry(JdkOrderEntry jdkOrderEntry,
                                                                      LinkedHashMap<VirtualFile, Integer> value) {
          final Sdk jdk = jdkOrderEntry.getJdk();
          if (jdk != null && processedSdk.add(jdk)) {
            addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.CLASSES));
          }
          return value;
        }
      }, myEntries);

    fillIndexes();
  }

  public LibraryRuntimeClasspathScope(Project project, LibraryOrderEntry entry) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    addAll(myEntries, entry.getRootFiles(OrderRootType.CLASSES));

    fillIndexes();
  }

  private void fillIndexes() {
    int i = 0;
    for (Map.Entry<VirtualFile, Integer> entry : myEntries.entrySet()) {
      entry.setValue(i++);
    }
  }

  private static void addAll(Map<VirtualFile, Integer> map, VirtualFile[] files) {
    for (VirtualFile file : files) {
      map.put(file, null);
    }
  }

  public int hashCode() {
    return myEntries.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null || object.getClass() != LibraryRuntimeClasspathScope.class) return false;

    final LibraryRuntimeClasspathScope that = (LibraryRuntimeClasspathScope)object;
    return that.myEntries.equals(myEntries);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.containsKey(getFileRoot(file));
  }

  @Nullable
  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isLibraryClassFile(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);

    //for (VirtualFile root : myEntries) {
    //  if (r1 == root) return 1;
    //  if (r2 == root) return -1;
    //}
    //return 0;

    Integer index1 = myEntries.get(r1);
    Integer index2 = myEntries.get(r2);

    if (index1 == null) {
      return index2 == null ? 0 : -1;
    }

    if (index2 == null) {
      return 1;
    }

    return index2.compareTo(index1);
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}
