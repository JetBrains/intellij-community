/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ModalityStateListener;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectLifecycleListener;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class UiActivityMonitorImpl extends UiActivityMonitor implements ModalityStateListener, Disposable {
  private final Map<Object, BusyImpl> myObjects = new HashMap<Object, BusyImpl>();

  public UiActivityMonitorImpl(Application application) {
    myObjects.put(null, new BusyObjectGlobalImpl());
    application.getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, new ProjectLifecycleListener() {
      @Override
      public void projectComponentsInitialized(Project project) {
      }

      @Override
      public void beforeProjectLoaded(@NotNull Project project) {
      }

      @Override
      public void afterProjectClosed(@NotNull Project project) {
        myObjects.remove(project);
      }
    });
  }

  @Override
  public void initComponent() {
    ProjectManager pm = ProjectManager.getInstance();
    initBusyObjectFor(pm.getDefaultProject());

    LaterInvocator.addModalityStateListener(this, this);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void beforeModalityStateChanged(boolean entering) {
    if (isUnitTestMode()) {
      maybeReady();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          maybeReady();
        }
      });
    }
  }

  public void maybeReady() {
    for (Map.Entry<Object, BusyImpl> entry : myObjects.entrySet()) {
      entry.getValue().onReady();
    }
  }

  @Override
  public BusyObject getBusy(@NotNull Project project, UiActivity ... toWatch) {
    return _getBusy(project);
  }

  @Override
  public BusyObject getBusy(UiActivity ... toWatch) {
    return _getBusy(null);
  }

  @Override
  public void addActivity(@NotNull final Project project, @NotNull final UiActivity activity) {
    addActivity(project, activity, getDefaultModalityState());
  }

  @Override
  public void addActivity(@NotNull final Project project, @NotNull final UiActivity activity, @NotNull final ModalityState effectiveModalityState) {
    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        if (!hasObjectFor(project)) {
          Project[] open = ProjectManager.getInstance().getOpenProjects();
          for (Project each : open) {
            if (each == project) {
              initBusyObjectFor(project);
              break;
            }
          }
        }

        _getBusy(project).addActivity(activity, allocation, effectiveModalityState);
      }
    });
  }

  @Override
  public void removeActivity(@NotNull final Project project, @NotNull final UiActivity activity) {
    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        _getBusy(project).removeActivity(activity);
      }
    });
  }

  @Override
  public void addActivity(@NotNull final UiActivity activity) {
    addActivity(activity, getDefaultModalityState());
  }

  private static ModalityState getDefaultModalityState() {
    return ApplicationManager.getApplication().getNoneModalityState();
  }

  @Override
  public void addActivity(@NotNull final UiActivity activity, @NotNull final ModalityState effectiveModalityState) {
    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        _getBusy(null).addActivity(activity, allocation, effectiveModalityState);
      }
    });
  }

  @Override
  public void removeActivity(@NotNull final UiActivity activity) {
    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        _getBusy(null).removeActivity(activity);
      }
    });
  }

  private BusyImpl _getBusy(@Nullable Object key) {
    BusyImpl object = myObjects.get(key);
    return object != null ? object : getGlobalBusy();
  }

  void initBusyObjectFor(@Nullable Object key) {
    BusyImpl object = new BusyImpl();
    myObjects.put(key, object);
  }

  boolean hasObjectFor(Project project) {
    return myObjects.containsKey(project);
  }

  private BusyObjectGlobalImpl getGlobalBusy() {
    return (BusyObjectGlobalImpl)myObjects.get(null);
  }

  public void clear() {
    for (Map.Entry<Object, BusyImpl> entry : myObjects.entrySet()) {
      entry.getValue().clear();
    }
  }



  @Override
  public void disposeComponent() {
    myObjects.clear();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UiActivityMonitor";
  }

  private static class ActivityInfo {
    private final Throwable myAllocation;
    private final ModalityState myEffectiveState;

    private ActivityInfo(@Nullable Throwable allocation, @NotNull ModalityState effectiveState) {
      myAllocation = allocation;
      myEffectiveState = effectiveState;
    }

    @Nullable
    public Throwable getAllocation() {
      return myAllocation;
    }

    @NotNull
    public ModalityState getEffectiveState() {
      return myEffectiveState;
    }
  }

  protected ModalityState getCurrentState() {
    return ModalityState.current();
  }

  private class BusyImpl extends BusyObject.Impl {

    private final Map<Object, ActivityInfo> myActivities = new HashMap<Object, ActivityInfo>();

    private final Set<Object> myQueuedToRemove = new HashSet<Object>();

    @Override
    public boolean isReady() {
      return isOwnReady() && getGlobalBusy().isOwnReady();
    }

    boolean isOwnReady() {
      if (myActivities.isEmpty()) return true;

      final ModalityState current = getCurrentState();
      for (Map.Entry<Object, ActivityInfo> entry : myActivities.entrySet()) {
        final ActivityInfo info = entry.getValue();
        if (!current.dominates(info.getEffectiveState())) {
          return false;
        }
      }

      return true;
    }


    public void addActivity(Object activity, Throwable allocation, ModalityState effectiveModalityState) {
      myActivities.put(activity, new ActivityInfo(allocation, effectiveModalityState));
      myQueuedToRemove.remove(activity);
    }

    public void removeActivity(final Object activity) {
      if (!myActivities.containsKey(activity)) return;

      myQueuedToRemove.add(activity);

      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (!myQueuedToRemove.contains(activity)) return;

          myQueuedToRemove.remove(activity);
          myActivities.remove(activity);
          onReady();
        }
      };
      if (isUnitTestMode()) {
        runnable.run();
      } else {
        SwingUtilities.invokeLater(runnable);
      }
    }

    public void clear() {
      Object[] activities = myActivities.keySet().toArray(new Object[myActivities.size()]);
      for (Object each : activities) {
        removeActivity(each);
      }
    }
  }

  private class BusyObjectGlobalImpl extends BusyImpl {

    @Override
    public boolean isReady() {
      for (Map.Entry<Object, BusyImpl> entry : myObjects.entrySet()) {
        BusyImpl busy = entry.getValue();
        if (busy == this) continue;
        if (!busy.isOwnReady()) return false;
      }

      return isOwnReady();
    }
  }

  private static void invokeLaterIfNeeded(final MyRunnable runnable) {
    final Throwable allocation = Registry.is("ide.debugMode") ? new Exception() : null;

    if (isUnitTestMode()) {
      runnable.run(allocation);
    } else {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          runnable.run(allocation);
        }
      });
    }
  }

  private interface MyRunnable {
    void run(Throwable allocation);
  }

  private static boolean isUnitTestMode() {
      Application app = ApplicationManager.getApplication();
      return app == null || app.isUnitTestMode();
  }

}
