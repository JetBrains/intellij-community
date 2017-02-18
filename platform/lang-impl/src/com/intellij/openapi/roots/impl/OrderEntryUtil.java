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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class OrderEntryUtil {
  private OrderEntryUtil() {
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@NotNull ModuleRootModel model, @Nullable Library library) {
    if (library == null) return null;
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }

    return null;
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@NotNull ModuleRootModel model, @NotNull String libraryName) {
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final String libName = ((LibraryOrderEntry)orderEntry).getLibraryName();
        if (libraryName.equals(libName)) {
          return (LibraryOrderEntry)orderEntry;
        }
      }
    }
    return null;
  }

  @Nullable
  public static ModuleOrderEntry findModuleOrderEntry(@NotNull ModuleRootModel model, @Nullable Module module) {
    if (module == null) return null;

    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)orderEntry).getModule())) {
        return (ModuleOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Nullable
  public static JdkOrderEntry findJdkOrderEntry(@NotNull ModuleRootModel model, @Nullable Sdk sdk) {
    if (sdk == null) return null;

    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry && sdk.equals(((JdkOrderEntry)orderEntry).getJdk())) {
        return (JdkOrderEntry)orderEntry;
      }
    }
    return null;
  }

  public static boolean equals(OrderEntry orderEntry1, OrderEntry orderEntry2) {
    if (orderEntry1 instanceof JdkOrderEntry && orderEntry2 instanceof JdkOrderEntry) {
      final JdkOrderEntry jdkOrderEntry1 = (JdkOrderEntry)orderEntry1;
      final JdkOrderEntry jdkOrderEntry2 = (JdkOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getJdk(), jdkOrderEntry2.getJdk()) &&
             Comparing.strEqual(jdkOrderEntry1.getJdkName(), jdkOrderEntry2.getJdkName());
    }
    if (orderEntry1 instanceof LibraryOrderEntry && orderEntry2 instanceof LibraryOrderEntry) {
      final LibraryOrderEntry jdkOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      final LibraryOrderEntry jdkOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getLibrary(), jdkOrderEntry2.getLibrary());
    }
    if (orderEntry1 instanceof ModuleSourceOrderEntry && orderEntry2 instanceof ModuleSourceOrderEntry) {
      final ModuleSourceOrderEntry jdkOrderEntry1 = (ModuleSourceOrderEntry)orderEntry1;
      final ModuleSourceOrderEntry jdkOrderEntry2 = (ModuleSourceOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getOwnerModule(), jdkOrderEntry2.getOwnerModule());
    }
    if (orderEntry1 instanceof ModuleOrderEntry && orderEntry2 instanceof ModuleOrderEntry) {
      final ModuleOrderEntry jdkOrderEntry1 = (ModuleOrderEntry)orderEntry1;
      final ModuleOrderEntry jdkOrderEntry2 = (ModuleOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getModule(), jdkOrderEntry2.getModule());
    }
    return false;
  }

  public static boolean equals(Library library1, Library library2) {
    if (library1 == library2) return true;
    if (library1 == null || library2 == null) return false;

    final LibraryTable table = library1.getTable();
    if (table != null) {
      if (library2.getTable() != table) return false;
      final String name = library1.getName();
      return name != null && name.equals(library2.getName());
    }

    if (library2.getTable() != null) return false;

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (!Comparing.equal(library1.getUrls(type), library2.getUrls(type))) {
        return false;
      }
    }
    return true;
  }

  public static void addLibraryToRoots(final LibraryOrderEntry libraryOrderEntry, final Module module) {
    Library library = libraryOrderEntry.getLibrary();
    if (library == null) return;
    addLibraryToRoots(module, library);
  }

  public static void addLibraryToRoots(@NotNull Module module, @NotNull Library library) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = manager.getModifiableModel();

    if (library.getTable() == null) {
      final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
      final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
      for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
        VirtualFile[] files = library.getFiles(orderRootType);
        for (VirtualFile jarFile : files) {
          libraryModel.addRoot(jarFile, orderRootType);
        }
      }
      libraryModel.commit();
    }
    else {
      rootModel.addLibraryEntry(library);
    }
    rootModel.commit();
  }

  private static int findLibraryOrderEntry(@NotNull OrderEntry[] entries, @NotNull Library library) {
    for (int i = 0; i < entries.length; i++) {
      OrderEntry entry = entries[i];
      if (entry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)entry).getLibrary())) {
        return i;
      }
    }
    return -1;
  }

  public static void replaceLibrary(@NotNull ModifiableRootModel model, @NotNull Library oldLibrary, @NotNull Library newLibrary) {
    int i = findLibraryOrderEntry(model.getOrderEntries(), oldLibrary);
    if (i == -1) return;

    model.addLibraryEntry(newLibrary);
    replaceLibraryByAdded(model, i);
  }

  public static void replaceLibraryEntryByAdded(@NotNull ModifiableRootModel model, @NotNull LibraryOrderEntry entry) {
    int i = ArrayUtil.indexOf(model.getOrderEntries(), entry);
    if (i == -1) return;

    replaceLibraryByAdded(model, i);
  }

  private static void replaceLibraryByAdded(ModifiableRootModel model, int toReplace) {
    OrderEntry[] entries = model.getOrderEntries();
    LibraryOrderEntry newEntry = (LibraryOrderEntry)entries[entries.length - 1];
    LibraryOrderEntry libraryEntry = (LibraryOrderEntry)entries[toReplace];
    boolean exported = libraryEntry.isExported();
    DependencyScope scope = libraryEntry.getScope();
    model.removeOrderEntry(libraryEntry);
    newEntry.setExported(exported);
    newEntry.setScope(scope);
    final OrderEntry[] newEntries = new OrderEntry[entries.length-1];
    System.arraycopy(entries, 0, newEntries, 0, toReplace);
    newEntries[toReplace] = newEntry;
    System.arraycopy(entries, toReplace + 1, newEntries, toReplace + 1, entries.length - toReplace - 2);
    model.rearrangeOrderEntries(newEntries);
  }

  public static <T extends OrderEntry> void processOrderEntries(@NotNull Module module,
                                                                @NotNull Class<T> orderEntryClass,
                                                                @NotNull Processor<T> processor) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntryClass.isInstance(orderEntry)) {
        if (!processor.process(orderEntryClass.cast(orderEntry))) {
          break;
        }
      }
    }
  }

  public static DependencyScope intersectScopes(DependencyScope scope1, DependencyScope scope2) {
    if (scope1 == scope2) return scope1;
    if (scope1 == DependencyScope.COMPILE) return scope2;
    if (scope2 == DependencyScope.COMPILE) return scope1;
    if (scope1 == DependencyScope.TEST || scope2 == DependencyScope.TEST) return DependencyScope.TEST;
    return scope1;
  }

  @NotNull
  public static List<Library> getModuleLibraries(@NotNull ModuleRootModel model) {
    OrderEntry[] orderEntries = model.getOrderEntries();
    List<Library> libraries = new ArrayList<>();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry entry = (LibraryOrderEntry)orderEntry;
        if (entry.isModuleLevel()) {
          libraries.add(entry.getLibrary());
        }
      }
    }
    return libraries;
  }
}
