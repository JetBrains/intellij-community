// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class LibraryRuntimeClasspathScope extends GlobalSearchScope {
  private final ProjectFileIndex myIndex;
  private final Object2IntMap<VirtualFile> myEntries = new Object2IntOpenHashMap<>();

  public LibraryRuntimeClasspathScope(@NotNull Project project, @NotNull Collection<? extends Module> modules) {
    super(project);

    myEntries.defaultReturnValue(-1);
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Set<Sdk> processedSdk = new HashSet<>();
    Set<Library> processedLibraries = new HashSet<>();
    Set<Module> processedModules = CollectionFactory.createSmallMemoryFootprintSet();
    for (Module module : modules) {
      buildEntries(module, processedModules, processedLibraries, processedSdk);
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

    LibraryRuntimeClasspathScope that = (LibraryRuntimeClasspathScope)object;
    return that.myEntries.equals(myEntries);
  }

  private void buildEntries(@NotNull Module module,
                            @NotNull Set<? super Module> processedModules,
                            @NotNull Set<? super Library> processedLibraries,
                            @NotNull Set<? super Sdk> processedSdk) {
    ModuleOrderEnumerator enumerator = new ModuleOrderEnumerator(ModuleRootManager.getInstance(module), null);
    enumerator.withProcessedModules(processedModules).recursively().process(new RootPolicy<>() {
      @Override
      public Object2IntMap<VirtualFile> visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry,
                                                                       Object2IntMap<VirtualFile> value) {
        Library library = libraryOrderEntry.getLibrary();
        if (library != null && processedLibraries.add(library)) {
          addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.CLASSES));
          addAll(value, libraryOrderEntry.getRootFiles(OrderRootType.SOURCES));
        }
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry moduleSourceOrderEntry,
                                                                    Object2IntMap<VirtualFile> value) {
        addAll(value, moduleSourceOrderEntry.getRootModel().getSourceRoots());
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitModuleOrderEntry(@NotNull ModuleOrderEntry moduleOrderEntry,
                                                                      Object2IntMap<VirtualFile> value) {
        Module depModule = moduleOrderEntry.getModule();
        if (depModule != null) {
          addAll(value, ModuleRootManager.getInstance(depModule).getSourceRoots());
        }
        return value;
      }

      @Override
      public Object2IntMap<VirtualFile> visitJdkOrderEntry(@NotNull JdkOrderEntry jdkOrderEntry,
                                                           Object2IntMap<VirtualFile> value) {
        Sdk jdk = jdkOrderEntry.getJdk();
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

  private @Nullable VirtualFile getFileRoot(@NotNull VirtualFile file) {
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
    VirtualFile r1 = getFileRoot(file1);
    VirtualFile r2 = getFileRoot(file2);
    int i1 = myEntries.getInt(r1);
    int i2 = myEntries.getInt(r2);
    if (i1 == i2) return 0;
    if (i1 == -1) return -1;
    if (i2 == -1) return 1;
    return i2 - i1;
  }

  @TestOnly
  public @NotNull List<VirtualFile> getRoots() {
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

  private static void addAll(@NotNull Object2IntMap<? super VirtualFile> entries, VirtualFile @NotNull [] files) {
    for (VirtualFile file : files) {
      if (!entries.containsKey(file)) {
        entries.put(file, entries.size());
      }
    }
  }
}
