// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.java.analysis.OuterModelsModificationTrackerManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
public final class OuterModelsModificationTrackerManagerImpl implements OuterModelsModificationTrackerManager, Disposable {
  private final OuterModelsModificationTracker myTracker;

  public OuterModelsModificationTrackerManagerImpl(@NotNull Project project) {
    myTracker = new OuterModelsModificationTracker(project, this, true, false);
  }

  @Override
  public ModificationTracker getTracker() {
    return myTracker;
  }

  @Override
  public void dispose() {

  }
}
