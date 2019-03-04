// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.shelf;

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager;
import org.jetbrains.annotations.NotNull;

public class ShelfViewUpdater implements VcsRepositoryMappingListener {

  private final ShelvedChangesViewManager myShelvedChangesViewManager;

  public ShelfViewUpdater(@NotNull Project project, @NotNull ShelvedChangesViewManager shelvedChangesViewManager) {
    myShelvedChangesViewManager = shelvedChangesViewManager;
    project.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, this);
  }

  @Override
  public void mappingChanged() {
    myShelvedChangesViewManager.updateOnVcsMappingsChanged();
  }
}
