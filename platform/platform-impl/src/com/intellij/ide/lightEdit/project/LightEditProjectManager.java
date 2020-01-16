// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditProjectManager {
  private static final Logger LOG = Logger.getInstance(LightEditService.class);
  private static final Object LOCK = new Object();

  private volatile LightEditProject myProject;

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Project getOrCreateProject() {
    if (myProject != null) {
      return myProject;
    }
    synchronized (LOCK) {
      long start = System.nanoTime();
      LightEditProject project = new LightEditProject();
      myProject = project;
      LOG.info(LightEditProject.class.getSimpleName() + " loaded in " + TimeoutUtil.getDurationMillis(start) + " ms");
      return project;
    }
  }

  public void close() {
    Project project = myProject;
    if (project != null) {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    }
    myProject = null;
  }

  public static boolean isLightEditProject(@Nullable Project project) {
    return project instanceof LightEditProject;
  }
}
