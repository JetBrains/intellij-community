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
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class UiActivityMonitorImpl extends UiActivityMonitor implements ModalityStateListener, Disposable {

  private final FactoryMap<Project, BusyContainer> myObjects = new FactoryMap<Project, BusyContainer>() {
    @Override
    protected BusyContainer create(Project key) {
      if (isEmpty()) {
        installListener();
      }
      return key == null ? new BusyContainer(key) : new BusyContainer(null) {
            @Override
            protected BusyImpl createBusyImpl(HashSet<UiActivity> key) {
              return new BusyImpl(key, this) {
                @Override
                public boolean isReady() {
                  for (Map.Entry<Project, BusyContainer> entry : myObjects.entrySet()) {
                    final BusyContainer eachContainer = entry.getValue();
                    final BusyImpl busy = eachContainer.getOrCreateBusy(myToWatchArray);
                    if (busy == this) continue;
                    if (!busy.isOwnReady()) return false;
                  }
                  return isOwnReady();
                }
              };
            }
      };
    }
  };

  private boolean myActive;

  private BusyObject myEmptyBusy = new BusyObject.Impl() {
    @Override
    public boolean isReady() {
      return true;
    }
  };

  public UiActivityMonitorImpl() {
  }

  public void installListener() {
    LaterInvocator.addModalityStateListener(this, this);
  }

  @Override
  public void dispose() {
    myObjects.clear();
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
    for (BusyContainer each : myObjects.values()) {
      each.onReady();
    }
  }

  @Override
  public BusyObject getBusy(@NotNull Project project, UiActivity... toWatch) {
    if (!isActive()) return myEmptyBusy;

    return _getBusy(project, toWatch);
  }

  @Override
  public BusyObject getBusy(UiActivity... toWatch) {
    if (!isActive()) return myEmptyBusy;

    return _getBusy(null, toWatch);
  }

  @Override
  public void addActivity(@NotNull final Project project, @NotNull final UiActivity activity) {
    addActivity(project, activity, getDefaultModalityState());
  }

  @Override
  public void addActivity(@NotNull final Project project,
                          @NotNull final UiActivity activity,
                          @NotNull final ModalityState effectiveModalityState) {
    if (!isActive()) return;


    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        getBusyContainer(project).addActivity(activity, allocation, effectiveModalityState);
      }
    });
  }

  @Override
  public void removeActivity(@NotNull final Project project, @NotNull final UiActivity activity) {
    if (!isActive()) return;

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
    if (!isActive()) return;

    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        getBusyContainer(null).addActivity(activity, allocation, effectiveModalityState);
      }
    });
  }

  @Override
  public void removeActivity(@NotNull final UiActivity activity) {
    if (!isActive()) return;

    invokeLaterIfNeeded(new MyRunnable() {
      @Override
      public void run(Throwable allocation) {
        _getBusy(null).removeActivity(activity);
      }
    });
  }

  private BusyImpl _getBusy(@Nullable Project key, UiActivity... toWatch) {
    return getBusyContainer(key).getOrCreateBusy(toWatch);
  }

  @NotNull
  private BusyContainer getBusyContainer(@Nullable Project key) {
    BusyContainer container = myObjects.get(key);
    return container != null ? container : getGlobalBusy();
  }

  void initBusyObjectFor(@Nullable Project key) {
    myObjects.put(key, new BusyContainer(key));
  }

  boolean hasObjectFor(Project project) {
    return myObjects.containsKey(project);
  }

  private BusyContainer getGlobalBusy() {
    return myObjects.get(null);
  }

  public void clear() {
    final Set<Project> keys = myObjects.keySet();
    for (Project each : keys) {
      myObjects.get(each).clear();
    }
  }

  @Override
  public void setActive(boolean active) {
    if (myActive == active) return;

    if (myActive && !active) {
      clear();
    }

    myActive = active;
  }

  public boolean isActive() {
    return myActive;
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

    private final Map<UiActivity, ActivityInfo> myActivities = new HashMap<UiActivity, ActivityInfo>();

    private final Set<UiActivity> myQueuedToRemove = new HashSet<UiActivity>();

    protected final Set<UiActivity> myToWatch;
    protected final UiActivity[] myToWatchArray;
    private UiActivityMonitorImpl.BusyContainer myContainer;

    private BusyImpl(Set<UiActivity> toWatch, BusyContainer container) {
      myToWatch = toWatch;
      myToWatchArray = toWatch.toArray(new UiActivity[toWatch.size()]);
      myContainer = container;
    }

    @Override
    public boolean isReady() {
      return isOwnReady() && getGlobalBusy().getOrCreateBusy(myToWatchArray).isOwnReady();
    }

    boolean isOwnReady() {
      Map<UiActivity, ActivityInfo> infoToCheck = new HashMap<UiActivity, ActivityInfo>();

      final Iterator<Set<UiActivity>> activitySets = myContainer.myActivities2Object.keySet().iterator();
      while (activitySets.hasNext()) {
        Set<UiActivity> eachActivitySet = activitySets.next();
        final BusyImpl eachBusyObject = myContainer.myActivities2Object.get(eachActivitySet);
        if (eachBusyObject == this) continue;

        for (UiActivity eachOtherActivity : eachActivitySet) {
          for (UiActivity eachToWatch : myToWatch) {
            if (eachToWatch.isSameOrGeneralFor(eachOtherActivity) && eachBusyObject.myActivities.containsKey(eachOtherActivity)) {
              infoToCheck.put(eachOtherActivity, eachBusyObject.myActivities.get(eachOtherActivity));
            }
          }
        }
      }

      infoToCheck.putAll(myActivities);

      if (infoToCheck.isEmpty()) return true;

      final ModalityState current = getCurrentState();
      for (Map.Entry<UiActivity, ActivityInfo> entry : infoToCheck.entrySet()) {
        final ActivityInfo info = entry.getValue();
        if (!current.dominates(info.getEffectiveState())) {
          return false;
        }
      }

      return true;
    }


    public void addActivity(UiActivity activity, Throwable allocation, ModalityState effectiveModalityState) {
      if (!myToWatch.isEmpty()) {
        if (!myToWatch.contains(activity)) return;
      }

      myActivities.put(activity, new ActivityInfo(allocation, effectiveModalityState));
      myQueuedToRemove.remove(activity);
      
      myContainer.onActivityAdded(this, activity);
    }

    public void removeActivity(final UiActivity activity) {
      if (!myActivities.containsKey(activity)) return;

      myQueuedToRemove.add(activity);

      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (!myQueuedToRemove.contains(activity)) return;

          myQueuedToRemove.remove(activity);
          myActivities.remove(activity);
          myContainer.onActivityRemoved(BusyImpl.this, activity);
          
          onReady();
        }
      };
      if (isUnitTestMode()) {
        runnable.run();
      }
      else {
        SwingUtilities.invokeLater(runnable);
      }
    }

    public void clear() {
      UiActivity[] activities = myActivities.keySet().toArray(new UiActivity[myActivities.size()]);
      for (UiActivity each : activities) {
        removeActivity(each);
      }
    }
  }

  private static void invokeLaterIfNeeded(final MyRunnable runnable) {
    final Throwable allocation = Registry.is("ide.debugMode") ? new Exception() : null;

    if (isUnitTestMode()) {
      runnable.run(allocation);
    }
    else {
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

  public class BusyContainer implements Disposable {

    private Map<Set<UiActivity>, BusyImpl> myActivities2Object = new HashMap<Set<UiActivity>, BusyImpl>();
    private Map<BusyImpl, Set<UiActivity>> myObject2Activities = new HashMap<BusyImpl, Set<UiActivity>>();

    private Set<UiActivity> myActivities = new HashSet<UiActivity>();

    private BusyImpl myDefault;

    private boolean myRemovingActivityNow;
    @Nullable private final Project myProject;

    public BusyContainer(@Nullable Project project) {
      myProject = project;
      myDefault = registerBusyObject(new HashSet<UiActivity>());
      if (project != null) {
        Disposer.register(project, this);
      }
    }

    public BusyImpl getOrCreateBusy(UiActivity... activities) {
      final HashSet<UiActivity> key = new HashSet<UiActivity>();
      key.addAll(Arrays.asList(activities));

      if (myActivities2Object.containsKey(key)) {
        return myActivities2Object.get(key);
      }
      else {
        return registerBusyObject(key);
      }
    }

    private BusyImpl registerBusyObject(HashSet<UiActivity> key) {
      final BusyImpl busy = createBusyImpl(key);
      myActivities2Object.put(key, busy);
      myObject2Activities.put(busy, key);
      return busy;
    }

    protected BusyImpl createBusyImpl(HashSet<UiActivity> key) {
      return new BusyImpl(key, this);
    }

    public void onReady() {
      final Iterator<Set<UiActivity>> keyIterator = myActivities2Object.keySet().iterator();
      while (keyIterator.hasNext()) {
        Set<UiActivity> eachKey = keyIterator.next();
        final BusyImpl busy = myActivities2Object.get(eachKey);
        busy.onReady();
        if (busy.isReady()) {
          keyIterator.remove();
          myObject2Activities.remove(busy);
        }
      }
    }

    public void clear() {
      final UiActivity[] activities = myActivities.toArray(new UiActivity[myActivities.size()]);
      for (UiActivity each : activities) {
        removeActivity(each);
      }
    }

    public void onActivityAdded(BusyImpl busy, UiActivity activity) {
      myActivities.add(activity);
    }

    public void onActivityRemoved(BusyImpl busy, UiActivity activity) {
      if (myRemovingActivityNow) return;

      final Map<BusyImpl, Set<UiActivity>> toRemove = new HashMap<BusyImpl, Set<UiActivity>>();

      try {
        myRemovingActivityNow = true;

        myActivities.remove(activity);
        final Iterator<BusyImpl> objects = myObject2Activities.keySet().iterator();
        while (objects.hasNext()) {
          BusyImpl each = objects.next();
          if (each != busy) {
            each.removeActivity(activity);
          }
          if (each.isReady()) {
            final Set<UiActivity> activities = myObject2Activities.get(busy);
            toRemove.put(busy, activities);
          }
        }
      }
      finally {
        for (BusyImpl each : toRemove.keySet()) {
          final Set<UiActivity> activities = myObject2Activities.remove(each);
          myActivities2Object.remove(activities);
        }

        myRemovingActivityNow = false;
      }
    }

    public void addActivity(UiActivity activity, Throwable allocation, ModalityState state) {
      getOrCreateBusy(activity);
      final Set<BusyImpl> busies = myObject2Activities.keySet();
      for (BusyImpl each : busies) {
        each.addActivity(activity, allocation, state);
      }
    }

    @Override
    public void dispose() {
      myObjects.remove(myProject);
    }
  }
}
