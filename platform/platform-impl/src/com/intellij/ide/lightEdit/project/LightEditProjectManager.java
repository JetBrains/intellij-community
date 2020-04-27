// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.project;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditProjectManager {
  private static final Logger LOG = Logger.getInstance(LightEditProjectManager.class);
  private static final Object LOCK = new Object();

  private volatile LightEditProjectImpl myProject;

  @Nullable
  public Project getProject() {
    return myProject;
  }

  public @NotNull Project getOrCreateProject() {
    LightEditProjectImpl project = myProject;
    if (project == null) {
      synchronized (LOCK) {
        if (myProject == null) {
          myProject = createProject();
        }
        project = myProject;
      }
    }
    return project;
  }

  private static @NotNull LightEditProjectImpl createProject() {
    long start = System.nanoTime();
    LightEditProjectImpl project = new LightEditProjectImpl();
    LOG.info(LightEditProjectImpl.class.getSimpleName() + " loaded in " + TimeoutUtil.getDurationMillis(start) + " ms");
    return project;
  }

  public void close() {
    Project project = myProject;
    if (project != null) {
      SaveAndSyncHandler.getInstance().saveSettingsUnderModalProgress(project);
      ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    }
    synchronized (LOCK) {
      myProject = null;
    }
  }
}
