// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.util.TimeoutUtil;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LightEditProjectManager {
  private static final Logger LOG = Logger.getInstance(LightEditProjectManager.class);
  private static final Object LOCK = new Object();

  private volatile LightEditProjectImpl myProject;

  public @Nullable Project getProject() {
    return myProject;
  }

  public @NotNull Project getOrCreateProject() {
    LightEditProjectImpl project = myProject;
    if (project == null) {
      boolean created = false;
      synchronized (LOCK) {
        if (myProject == null) {
          myProject = createProject();
          created = true;
        }
        project = myProject;
      }
      if (created) {
        fireProjectOpened(project);
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
          @Override
          public void projectClosed(@NotNull Project project) {
            if (project == myProject) {
              synchronized (LOCK) {
                myProject = null;
              }
            }
          }
        });
      }
    }
    return project;
  }

  private static void fireProjectOpened(@NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    Runnable fireRunnable = () -> {
      // similar to com.intellij.openapi.project.impl.ProjectManagerExImplKt.openProject
      app.getMessageBus().syncPublisher(ProjectManager.TOPIC).projectOpened(project);
      try {
        BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, continuation) -> {
          ((StartupManagerImpl)StartupManager.getInstance(project)).projectOpened(null, continuation);
          return null;
        });
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    };
    if (app.isDispatchThread() || app.isUnitTestMode()) {
      fireRunnable.run();
    }
    else {
      // Initialize ActionManager out of EDT to pass "assert !app.isDispatchThread()" in ActionManagerImpl
      ActionManager.getInstance();
      app.invokeLater(fireRunnable);
    }
  }

  private static @NotNull LightEditProjectImpl createProject() {
    long start = System.nanoTime();
    LightEditProjectImpl project = new LightEditProjectImpl();
    LOG.info(LightEditProjectImpl.class.getSimpleName() + " loaded in " + TimeoutUtil.getDurationMillis(start) + " ms");
    return project;
  }
}
