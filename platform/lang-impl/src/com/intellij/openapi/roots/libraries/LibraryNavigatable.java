// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class LibraryNavigatable implements Navigatable {
  private final @NotNull Library myLibrary;
  private final @NotNull Project myProject;
  private final @Nullable Module module;
  private OrderEntry element;

  public LibraryNavigatable(@NotNull Library library, @NotNull Project project, @Nullable Module module) {
    myLibrary = library;
    myProject = project;
    this.module = module;
    if (module != null) {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof LibraryOrderEntry) {
          if (((LibraryOrderEntry)entry).getLibrary() == library) {
            element = entry;
          }
        }
      }
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (element != null) {
      ProjectSettingsService.getInstance(myProject).openLibraryOrSdkSettings(element);
    }
    else {
      ProjectSettingsService.getInstance(myProject).openLibrary(myLibrary);
    }
  }

  @Override
  public boolean canNavigate() {
    return module == null || (!module.isDisposed() && element != null);
  }
}
