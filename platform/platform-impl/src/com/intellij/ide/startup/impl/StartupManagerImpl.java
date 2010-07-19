/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.startup.impl;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StartupManagerImpl extends StartupManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private final List<Runnable> myPreStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> myStartupActivities = new ArrayList<Runnable>();
  private final List<Runnable> myPostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());

  private final List<CacheUpdater> myCacheUpdaters = new LinkedList<CacheUpdater>();

  private volatile boolean myPreStartupActivitiesPassed = false;
  private volatile boolean myStartupActivitiesRunning = false;
  private volatile boolean myStartupActivitiesPassed = false;
  private volatile boolean myPostStartupActivitiesPassed = false;

  private final Project myProject;

  public StartupManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void registerPreStartupActivity(@NotNull Runnable runnable) {
    LOG.assertTrue(!myPreStartupActivitiesPassed, "Registering pre startup activity that will never be run");
    myPreStartupActivities.add(runnable);
  }

  @Override
  public void registerStartupActivity(Runnable runnable) {
    LOG.assertTrue(!myStartupActivitiesPassed, "Registering startup activity that will never be run");
    myStartupActivities.add(runnable);
  }

  @Override
  public synchronized void registerPostStartupActivity(Runnable runnable) {
    LOG.assertTrue(!myPostStartupActivitiesPassed, "Registering post-startup activity that will never be run");
    myPostStartupActivities.add(runnable);
  }

  @Override
  public void registerCacheUpdater(CacheUpdater updater) {
    LOG.assertTrue(!myStartupActivitiesPassed, CacheUpdater.class.getSimpleName() + " must be registered before startup activity finished");
    myCacheUpdaters.add(updater);
  }

  @Override
  public boolean startupActivityRunning() {
    return myStartupActivitiesRunning;
  }

  @Override
  public boolean startupActivityPassed() {
    return myStartupActivitiesPassed;
  }

  @Override
  public boolean postStartupActivityPassed() {
    return myPostStartupActivitiesPassed;
  }

  public void runStartupActivities() {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        HeavyProcessLatch.INSTANCE.processStarted();
        try {
          runActivities(myPreStartupActivities);
          myPreStartupActivitiesPassed = true;

          myStartupActivitiesRunning = true;
          runActivities(myStartupActivities);

          myStartupActivitiesRunning = false;

          startCacheUpdate();
          myStartupActivitiesPassed = true;
        }
        finally {
          HeavyProcessLatch.INSTANCE.processFinished();
        }
      }
    });
  }

  public synchronized void runPostStartupActivities() {
    Application app = ApplicationManager.getApplication();
    app.assertIsDispatchThread();

    if (myPostStartupActivitiesPassed) return;

    final List<Runnable> dumbAware = CollectionFactory.arrayList();
    final List<Runnable> nonDumbAware = CollectionFactory.arrayList();

    for (Runnable each : myPostStartupActivities) {
      if (DumbService.isDumbAware(each)) {
        dumbAware.add(each);
      }
      else {
        nonDumbAware.add(each);
      }
    }

    runActivities(dumbAware);
    DumbService.getInstance(myProject).runWhenSmart(new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        runActivities(nonDumbAware);

        myPostStartupActivitiesPassed = true;
        myPostStartupActivities.clear();
      }
    });

    if (!app.isUnitTestMode()) {
      VirtualFileManager.getInstance().refresh(!app.isHeadlessEnvironment());
    }
  }

  private void startCacheUpdate() {
    try {
      DumbServiceImpl.getInstance(myProject).queueCacheUpdate(myCacheUpdaters);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static void runActivities(final List<Runnable> activities) {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    while (!activities.isEmpty()) {
      final Runnable runnable = activities.remove(0);
      if (indicator != null) indicator.checkCanceled();

      try {
        runnable.run();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable ex) {
        LOG.error(ex);
      }
    }
  }

  public void runWhenProjectIsInitialized(final Runnable action) {
    final Runnable runnable;

    final Application application = ApplicationManager.getApplication();
    if (DumbService.isDumbAware(action)) {
      runnable = new DumbAwareRunnable() {
        public void run() {
          application.runWriteAction(action);
        }
      };
    }
    else {
      runnable = new Runnable() {
        public void run() {
          application.runWriteAction(action);
        }
      };
    }

    if (myProject.isInitialized() || (application.isUnitTestMode() && myPostStartupActivitiesPassed)) {
      // in tests which simulate project opening, post-startup activities could have been run already. Then we should act as if the project was initialized
      if (application.isDispatchThread()) {
        runnable.run();
      }
      else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            if (!myProject.isDisposed()) {
              runnable.run();
            }
          }
        });
      }
    }
    else {
      registerPostStartupActivity(runnable);
    }
  }

  @TestOnly
  public void prepareForNextTest() {
    myPreStartupActivities.clear();
    myStartupActivities.clear();
    myPostStartupActivities.clear();
    myCacheUpdaters.clear();

    myPreStartupActivitiesPassed = false;
    myStartupActivitiesRunning = false;
    myStartupActivitiesPassed = false;
    myPostStartupActivitiesPassed = false;
  }

  @TestOnly
  public void checkCleared() {
    try {
      assert myStartupActivities.isEmpty() : "Activities: " + myStartupActivities;
      assert myPostStartupActivities.isEmpty() : "Post Activities: " + myPostStartupActivities;
      assert myPreStartupActivities.isEmpty() : "Pre Activities: " + myPreStartupActivities;
    }
    finally {
      prepareForNextTest();
    }
  }
}
