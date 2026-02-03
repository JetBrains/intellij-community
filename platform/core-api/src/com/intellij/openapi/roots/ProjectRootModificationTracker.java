// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;

public abstract class ProjectRootModificationTracker implements ModificationTracker {
  private static final ProjectRootModificationTracker NEVER_CHANGED = new ProjectRootModificationTracker() {
    @Override
    public long getModificationCount() {
      return 0;
    }
  };

  public static ProjectRootModificationTracker getInstance(Project project) {
    ProjectRootModificationTracker instance = project.getService(ProjectRootModificationTracker.class);
    if (instance == null) return NEVER_CHANGED;
    return instance;
  }
}
