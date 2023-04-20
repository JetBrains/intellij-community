// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@Service
@ApiStatus.Internal
public final class OuterModelsModificationTrackerManager implements Disposable {
  private final OuterModelsModificationTracker myTracker;

  @NotNull
  public static OuterModelsModificationTrackerManager getInstance(@NotNull Project project) {
    return project.getService(OuterModelsModificationTrackerManager.class);
  }

  public OuterModelsModificationTrackerManager(@NotNull Project project) {
    myTracker = new OuterModelsModificationTracker(project, this, true);
  }

  public ModificationTracker getTracker() {
    return myTracker;
  }

  @Override
  public void dispose() {

  }
}
