// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract CellAppearanceEx forOrderEntry(Project project, @NotNull OrderEntry orderEntry, boolean selected);

  @NotNull
  public abstract CellAppearanceEx forLibrary(Project project, @NotNull Library library, boolean hasInvalidRoots);

  @NotNull
  public abstract CellAppearanceEx forJdk(@Nullable Sdk jdk, boolean isInComboBox, boolean selected, boolean showVersion);

  @NotNull
  public abstract CellAppearanceEx forContentFolder(@NotNull ContentFolder folder);

  @NotNull
  public abstract CellAppearanceEx forModule(@NotNull Module module);
}
