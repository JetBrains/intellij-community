// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class BuildViewManager extends AbstractViewManager {

  public BuildViewManager(Project project) {
    super(project);
  }

  @Override
  public @NotNull String getViewName() {
    return LangBundle.message("tab.title.build.output");
  }


  @ApiStatus.Experimental
  public static BuildProgress<BuildProgressDescriptor> createBuildProgress(@NotNull Project project) {
    return new BuildRootProgressImpl(project.getService(BuildViewManager.class));
  }
}
