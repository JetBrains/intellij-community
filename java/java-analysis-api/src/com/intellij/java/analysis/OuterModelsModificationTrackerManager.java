// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provides ModificationTracker incremented if changes in file (class) of VFS could change the number of stereotype components scanned
 * by "component scan" models (@ComponentScan/<ctx:component-scan ../>/ repositories scan, etc.)
 * <p/>
 * VirtualFileListener: count++ if file was added/moved/deleted. This file could be scanned by component scan (if it's stereotype)
 * PsiTreeChangeListener: count++ on: adding/removing/editing annotations and adding/removing of inner classes.
 */
@ApiStatus.Experimental
public interface OuterModelsModificationTrackerManager {
  @ApiStatus.Experimental
  static OuterModelsModificationTrackerManager getInstance(@NotNull Project project) {
    return project.getService(OuterModelsModificationTrackerManager.class);
  }

  @ApiStatus.Experimental
  static ModificationTracker getTracker(@NotNull Project project) {
    return getInstance(project).getTracker();
  }

  ModificationTracker getTracker();
}
