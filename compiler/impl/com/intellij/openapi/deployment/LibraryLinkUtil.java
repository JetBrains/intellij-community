/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.deployment;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class LibraryLinkUtil {
  @Nullable
  public static Library findModuleLibrary(Module module, @Nullable ModulesProvider provider, @Nullable String name, String url) {
    if (url == null && name == null) return null;
    if (provider == null) {
      provider = new DefaultModulesProvider(module.getProject());
    }
    return findModuleLibrary(module, provider, url, name, new HashSet<Module>());
  }

  @Nullable
  public static Library findModuleLibrary(Module module, final @NotNull ModulesProvider provider,
                                                     String url, @Nullable String name, Set<Module> visited) {
    if (!visited.add(module)) {
      return null;
    }

    ModuleRootModel rootModel = provider.getRootModel(module);
    OrderEntry[] orderEntries = rootModel.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module dependency = ((ModuleOrderEntry)orderEntry).getModule();
        if (dependency != null) {
          final Library library = findModuleLibrary(dependency, provider, url, name, visited);
          if (library != null) {
            return library;
          }
        }
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libraryOrderEntry = ((LibraryOrderEntry)orderEntry);
        Library library = libraryOrderEntry.getLibrary();
        if (library == null) continue;

        if (name != null && library.getTable()==null && name.equals(library.getName())) {
          return library;
        }

        if (name == null) {
          String[] urls = library.getUrls(OrderRootType.CLASSES);
          if (urls.length == 1 && Comparing.strEqual(urls[0], url)) return library;
        }
      }
    }
    return null;
  }
}
