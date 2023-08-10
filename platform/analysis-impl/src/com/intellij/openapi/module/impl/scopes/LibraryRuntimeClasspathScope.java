// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final Object2IntMap<VirtualFile> myEntries=new Object2IntOpenHashMap<>();

  public LibraryRuntimeClasspathScope(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    super(project);

    myEntries.defaultReturnValue(-1);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Set<Sdk> processedSdk = new HashSet<>();
    final Set<Library> processedLibraries = new HashSet<>();
    final Set<Module> processedModules = new HashSet<>();
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
    addAll(myEntries, entry.getRootFiles(OrderRootType.CLASSES));
    addAll(myEntries, entry.getRootFiles(OrderRootType.SOURCES));
  }

  @Override
  public int calcHashCode() {
    return myEntries.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object == this) return true;
    if (object == null || object.getClass() != LibraryRuntimeClasspathScope.class) return false;

    final LibraryRuntimeClasspathScope that = (LibraryRuntimeClasspathScope)object;
    return that.myEntries.equals(myEntries);
  }

  private void buildEntries(@NotNull final Module module,
                            @NotNull final Set<? super Module> processedModules,
                            @NotNull final Set<? super Library> processedLibraries,
                            @NotNull final Set<? super Sdk> processedSdk,
                            @NotNull Condition<? super OrderEntry> condition) {
    if (!processedModules.add(module)) return;

    ModuleRootManager.getInstance(module).orderEntries().recursively().satisfying(condition).process(new RootPolicy<>() {
      @Override
      public Object2IntMap<VirtualFile> visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry,
                                                                       final Object2IntMap<VirtualFile> value) {
        final Library library = libraryOrderEntry.getLibrary();
        if (library != null && processedLibraries.add(library)) {
          addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.CLASSES));
          addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.SOURCES));
        }
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitModuleSourceOrderEntry(@NotNull final ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                            final Object2IntMap<VirtualFile> value) {
        processedModules.add(moduleSourceOrderEntry.getOwnerModule());
        addAll(value, moduleSourceOrderEntry.getRootModel().getSourceRoots());
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry,
                                                                      Object2IntMap<VirtualFile> value) {
        final Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          addAll(value, ModuleRootManager.getInstance(depModule).getSourceRoots());
        }
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitJdkOrderEntry(@NotNull final JdkOrderEntry jdkOrderEntry,
                                                                   final Object2IntMap<VirtualFile> value) {
        final Sdk jdk = jdkOrderEntry.getJdk();
        if (jdk != null && processedSdk.add(jdk)) {
          addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.CLASSES));
          addAll(value, jdkOrderEntry.getRootFiles(OrderRootType.SOURCES));
        }
        return value;
      }
    }, myEntries);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return myEntries.containsKey(getFileRoot(file));
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
    final int i1 = myEntries.getInt(r1);
    final int i2 = myEntries.getInt(r2);
    if (i1 == i2) return 0;
    if (i1 == -1) return -1;
    if (i2 == -1) return 1;
    return i2 - i1;
  }

  @TestOnly
  @NotNull
  public List<VirtualFile> getRoots() {
    if (myEntries.isEmpty()) return Collections.emptyList();
    VirtualFile[] result = new VirtualFile[myEntries.size()];
    for (Object2IntMap.Entry<VirtualFile> entry : myEntries.object2IntEntrySet()) {
      result[entry.getIntValue()] = entry.getKey();
    }
    return Arrays.asList(result);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return false;
  }

  @Override
  public boolean isSearchInLibraries() {
    return true;
  }

  private static void addAll(Object2IntMap<? super VirtualFile> entries,
                             VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!entries.containsKey(file)) {
        entries.put(file, entries.size());
      }
    }
  }
}
