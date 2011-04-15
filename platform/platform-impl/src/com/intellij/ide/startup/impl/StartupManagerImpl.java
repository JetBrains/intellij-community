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

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StartupManagerImpl extends StartupManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.impl.StartupManagerImpl");

  private final List<Runnable> myPreStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> myStartupActivities = new ArrayList<Runnable>();

  private final List<Runnable> myDumbAwarePostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private final List<Runnable> myNotDumbAwarePostStartupActivities = Collections.synchronizedList(new ArrayList<Runnable>());
  private boolean myPostStartupActivitiesPassed = false; // guarded by this

  private final List<CacheUpdater> myCacheUpdaters = new LinkedList<CacheUpdater>();
  private volatile boolean myPreStartupActivitiesPassed = false;
  private volatile boolean myStartupActivitiesRunning = false;
  private volatile boolean myStartupActivitiesPassed = false;

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
  public void registerStartupActivity(@NotNull Runnable runnable) {
    LOG.assertTrue(!myStartupActivitiesPassed, "Registering startup activity that will never be run");
    myStartupActivities.add(runnable);
  }

  @Override
  public synchronized void registerPostStartupActivity(@NotNull Runnable runnable) {
    LOG.assertTrue(!myPostStartupActivitiesPassed, "Registering post-startup activity that will never be run");
    (DumbService.isDumbAware(runnable) ? myDumbAwarePostStartupActivities : myNotDumbAwarePostStartupActivities).add(runnable);
  }

  @Override
  public void registerCacheUpdater(@NotNull CacheUpdater updater) {
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
  public synchronized boolean postStartupActivityPassed() {
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

          myStartupActivitiesPassed = true;
        }
        finally {
          HeavyProcessLatch.INSTANCE.processFinished();
        }
      }
    });
  }

  public synchronized void runPostStartupActivities() {
    final Application app = ApplicationManager.getApplication();
    app.assertIsDispatchThread();

    if (myPostStartupActivitiesPassed) return;

    runActivities(myDumbAwarePostStartupActivities);
    DumbService.getInstance(myProject).runWhenSmart(new Runnable() {
      public void run() {
        synchronized (StartupManagerImpl.this) {
          app.assertIsDispatchThread();
          if (myProject.isDisposed()) return;
          runActivities(myDumbAwarePostStartupActivities); // they can register activities while in the dumb mode
          runActivities(myNotDumbAwarePostStartupActivities);

          myPostStartupActivitiesPassed = true;
        }
      }
    });

    if (!app.isUnitTestMode()) {
      VirtualFileManager.getInstance().refresh(!app.isHeadlessEnvironment());
    }

    if (SystemInfo.isMac && SystemInfo.isMacIntel64 && "10.6".compareTo(SystemInfo.OS_VERSION) <= 0) {
      if (Registry.is("ide.firstStartup")) {
        String productName = ApplicationNamesInfo.getInstance().getProductName();
        Notification n =
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Optimization Hint", productName + " is now running in 64-bit mode.<br>" +
                                                                                               "You may reduce memory consumption by running it in 32-bit mode. " +
                                                                                               "<a href=\"http://devnet.jetbrains.net/docs/DOC-1232\">Click for details</a>", NotificationType.INFORMATION, new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              BrowserUtil.launchBrowser(event.getURL().toString());
            }
          });
        Notifications.Bus.notify(n, NotificationDisplayType.STICKY_BALLOON, null);
      }
    }

    Registry.get("ide.firstStartup").setValue(false);
  }

  public void startCacheUpdate() {
    try {
      DumbServiceImpl.getInstance(myProject).queueCacheUpdateInDumbMode(myCacheUpdaters);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static void runActivities(@NotNull List<Runnable> activities) {
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

  public synchronized void runWhenProjectIsInitialized(@NotNull final Runnable action) {
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

    if (myProject.isInitialized() || application.isUnitTestMode() && myPostStartupActivitiesPassed) {
      // in tests which simulate project opening, post-startup activities could have been run already.
      // Then we should act as if the project was initialized
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            runnable.run();
          }
        }
      });
    }
    else {
      registerPostStartupActivity(runnable);
    }
  }

  @TestOnly
  public synchronized void prepareForNextTest() {
    myPreStartupActivities.clear();
    myStartupActivities.clear();
    myDumbAwarePostStartupActivities.clear();
    myNotDumbAwarePostStartupActivities.clear();
    myCacheUpdaters.clear();
  }

  @TestOnly
  public synchronized void checkCleared() {
    try {
      assert myStartupActivities.isEmpty() : "Activities: " + myStartupActivities;
      assert myDumbAwarePostStartupActivities.isEmpty() : "DumbAware Post Activities: " + myDumbAwarePostStartupActivities;
      assert myNotDumbAwarePostStartupActivities.isEmpty() : "Post Activities: " + myNotDumbAwarePostStartupActivities;
      assert myPreStartupActivities.isEmpty() : "Pre Activities: " + myPreStartupActivities;
    }
    finally {
      prepareForNextTest();
    }
  }
}
