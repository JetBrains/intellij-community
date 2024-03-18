// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class LibraryNavigatable implements Navigatable {
  private final Module module;
  private OrderEntry element;

  public LibraryNavigatable(@NotNull Library library, @NotNull Module module) {
    this.module = module;
    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        if (((LibraryOrderEntry)entry).getLibrary() == library) {
          element = entry;
        }
      }
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    ProjectSettingsService.getInstance(module.getProject()).openLibraryOrSdkSettings(element);
  }

  @Override
  public boolean canNavigate() {
    return !module.isDisposed() && element != null;
  }
}
