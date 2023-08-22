// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.build.progress.BuildProgress;
import com.intellij.build.progress.BuildProgressDescriptor;
import com.intellij.build.progress.BuildRootProgressImpl;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public class SyncViewManager extends AbstractViewManager {
  public SyncViewManager(Project project) {
    super(project);
  }

  @NotNull
  @Override
  public String getViewName() {
    return LangBundle.message("sync.view.title");
  }

  @ApiStatus.Experimental
  public static BuildProgress<BuildProgressDescriptor> createBuildProgress(@NotNull Project project) {
    return new BuildRootProgressImpl(project.getService(SyncViewManager.class));
  }
}