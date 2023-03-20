// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.shelf;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import org.jetbrains.annotations.NotNull;

public final class ShelfViewUpdater implements VcsRepositoryMappingListener {
  private final Project myProject;

  public ShelfViewUpdater(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void mappingChanged() {
    ShelvedChangesViewManager shelveViewManager = myProject.getServiceIfCreated(ShelvedChangesViewManager.class);
    if (shelveViewManager != null) shelveViewManager.updateOnVcsMappingsChanged();
  }
}
