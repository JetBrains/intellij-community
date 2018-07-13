// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author max
 */
public class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final IndexedSet<VirtualFile> myEntries = new IndexedSet<>();

  private int myCachedHashCode;

  public LibraryRuntimeClasspathScope(@NotNull Project project, @NotNull Collection<Module> modules) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Set<Sdk> processedSdk = new THashSet<>();
    final Set<Library> processedLibraries = new THashSet<>();
    final Set<Module> processedModules = new THashSet<>();
    final Condition<OrderEntry> condition = orderEntry -> {
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module module = ((ModuleOrderEntry)orderEntry).getModule();
        return module != null && !processedModules.contains(module);
      }
      return true;
    };
    for (Module module : modules) {
      buildEntries(module, processedModules, processedLibraries, processedSdk, condition);
    }
  }

  public LibraryRuntimeClasspathScope(@NotNull Project project, @NotNull LibraryOrderEntry entry) {
    super(project);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Collections.addAll(myEntries, entry.getRootFiles(OrderRootType.CLASSES));
    Collections.addAll(myEntries, entry.getRootFiles(OrderRootType.SOURCES));
  }

  public int hashCode() {
    if (myCachedHashCode == 0) {
      myCachedHashCode = myEntries.hashCode();
    }

    return myCachedHashCode;
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null || object.getClass() != LibraryRuntimeClasspathScope.class) return false;

    final LibraryRuntimeClasspathScope that = (LibraryRuntimeClasspathScope)object;
    return that.myEntries.equals(myEntries);
  }

  private void buildEntries(@NotNull final Module module,
                            @NotNull final Set<Module> processedModules,
                            @NotNull final Set<Library> processedLibraries,
                            @NotNull final Set<Sdk> processedSdk,
                            @NotNull Condition<OrderEntry> condition) {
    if (!processedModules.add(module)) return;

    ModuleRootManager.getInstance(module).orderEntries().recursively().satisfying(condition).process(new RootPolicy<Set<VirtualFile>>() {
      @Override
      public Set<VirtualFile> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry libraryOrderEntry,
                                                     final Set<VirtualFile> value) {
        final Library library = libraryOrderEntry.getLibrary();
        if (library != null && processedLibraries.add(library)) {
          ContainerUtil.addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.CLASSES));
          ContainerUtil.addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.SOURCES));
        }
        return value;
      }

      @Override
      public Set<VirtualFile> visitModuleSourceOrderEntry(@NotNull final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                          final Set<VirtualFile> value) {
        processedModules.add(moduleSourceOrderEntry.getOwnerModule());
        ContainerUtil.addAll(value, moduleSourceOrderEntry.getRootModel().getSourceRoots());
        return value;
      }

      @Override
      public Set<VirtualFile> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry, Set<VirtualFile> value) {
        final Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          ContainerUtil.addAll(value, ModuleRootManager.getInstance(depModule).getSourceRoots());
        }
        return value;
      }

      @Override
      public Set<VirtualFile> visitJdkOrderEntry(@NotNull final JdkOrderEntry jdkOrderEntry, final Set<VirtualFile> value) {
        final Sdk jdk = jdkOrderEntry.getJdk();
        if (jdk != null && processedSdk.add(jdk)) {
          ContainerUtil.addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.CLASSES));
          ContainerUtil.addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.SOURCES));
        }
        return value;
      }
    }, myEntries);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  @Nullable
  private VirtualFile getFileRoot(@NotNull VirtualFile file) {
    if (myIndex.isInContent(file) || myIndex.isInLibrarySource(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    if (myIndex.isInLibraryClasses(file)) {
      return myIndex.getClassRootForFile(file);
    }
    return null;
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    if (file1.equals(file2)) return 0;
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    final int i1 = myEntries.indexOf(r1);
    final int i2 = myEntries.indexOf(r2);
    if (i1 == i2) return 0;
    if (i1 == -1) return -1;
    if (i2 == -1) return 1;
    return i2 - i1;
  }

  @TestOnly
  @NotNull
  public List<VirtualFile> getRoots() {
    return new ArrayList<>(myEntries);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }
}
