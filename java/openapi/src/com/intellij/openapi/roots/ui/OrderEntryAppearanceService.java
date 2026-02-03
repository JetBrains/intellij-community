// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class OrderEntryAppearanceService {
  public static OrderEntryAppearanceService getInstance() {
    return ApplicationManager.getApplication().getService(OrderEntryAppearanceService.class);
  }

  public abstract @NotNull CellAppearanceEx forOrderEntry(Project project, @NotNull OrderEntry orderEntry, boolean selected);

  public abstract @NotNull CellAppearanceEx forLibrary(Project project, @NotNull Library library, boolean hasInvalidRoots);

  public abstract @NotNull CellAppearanceEx forJdk(@Nullable Sdk jdk, boolean isInComboBox, boolean selected, boolean showVersion);

  public abstract @NotNull CellAppearanceEx forContentFolder(@NotNull ContentFolder folder);

  public abstract @NotNull CellAppearanceEx forModule(@NotNull Module module);
}
