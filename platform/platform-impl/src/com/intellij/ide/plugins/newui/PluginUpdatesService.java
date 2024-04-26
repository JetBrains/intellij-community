// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginUpdatesService {
  private static final Logger LOG = Logger.getInstance(PluginUpdatesService.class);
  private static final List<PluginUpdatesService> SERVICES = new ArrayList<>();
  private static final Object ourLock = new Object();
  private static final @NotNull Condition<IdeaPluginDescriptor> DEFAULT_FILTER = // only enabled plugins by default
    descriptor -> !PluginManagerCore.isDisabled(descriptor.getPluginId());

  // clients should receive filtered updates by default
  private static @Nullable Collection<IdeaPluginDescriptor> ourAllUpdates;
  private static @NotNull Condition<? super IdeaPluginDescriptor> ourFilter = DEFAULT_FILTER;
  // ourFilteredUpdates is in sync with ourAllUpdates at all times
  private static @Nullable Collection<IdeaPluginDescriptor> ourFilteredUpdates;
  private static boolean ourPrepared;
  private static boolean ourPreparing;
  private static boolean ourReset;

  private Consumer<? super Integer> myCountCallback;
  private List<Consumer<? super Collection<IdeaPluginDescriptor>>> myUpdateCallbacks;
  private boolean mySetFilter;

  static {
    PluginStateManager.addStateListener(new PluginStateListener() {
      @Override
      public void install(@NotNull IdeaPluginDescriptor descriptor) {
        finishUpdate(descriptor);
      }

      @Override
      public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
        finishUpdate(descriptor);
      }
    });
  }

  public static @NotNull PluginUpdatesService connectWithCounter(@NotNull Consumer<? super Integer> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myCountCallback = callback;

    synchronized (ourLock) {
      SERVICES.add(service);

      if (ourPrepared) {
        callback.accept(getCount());
        return service;
      }
    }

    calculateUpdates();
    return service;
  }

  public static @NotNull PluginUpdatesService connectWithUpdates(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myUpdateCallbacks = Collections.singletonList(callback);

    synchronized (ourLock) {
      SERVICES.add(service);

      if (ourPrepared) {
        callback.accept(ourFilteredUpdates);
      }
    }

    return service;
  }

  public void calculateUpdates(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    synchronized (ourLock) {
      if (myUpdateCallbacks == null) {
        myUpdateCallbacks = new ArrayList<>();
      }
      myUpdateCallbacks.add(callback);

      if (ourPrepared) {
        callback.accept(ourFilteredUpdates);
        return;
      }
    }
    calculateUpdates();
  }

  private static void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    synchronized (ourLock) {
      if (!ourPrepared || ourAllUpdates == null) {
        return;
      }

      for (Iterator<IdeaPluginDescriptor> I = ourAllUpdates.iterator(); I.hasNext(); ) {
        IdeaPluginDescriptor downloadedDescriptor = I.next();

        if (Objects.equals(downloadedDescriptor.getPluginId(), descriptor.getPluginId())) {
          I.remove();
          syncFilteredUpdates();

          Integer countValue = getCount();
          for (PluginUpdatesService service : SERVICES) {
            service.runCountCallbacks(countValue);
          }

          return;
        }
      }
    }
  }

  public void finishUpdate() {
    synchronized (ourLock) {
      if (!ourPrepared || ourAllUpdates == null) {
        return;
      }

      Integer countValue = getCount();
      for (PluginUpdatesService service : SERVICES) {
        service.runCountCallbacks(countValue);
      }
    }
  }

  public void recalculateUpdates() {
    synchronized (ourLock) {
      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(null);
      }

      if (ourPreparing) {
        resetUpdates();
      }
      else {
        calculateUpdates();
      }
    }
  }

  private static void resetUpdates() {
    ourReset = true;
  }

  public void setFilter(@NotNull Condition<? super IdeaPluginDescriptor> filter) {
    synchronized (ourLock) {
      if (!mySetFilter && ourFilter != DEFAULT_FILTER) {
        LOG.warn("Filter already set to " + ourFilter + ", new filter " + filter + " will be ignored", new Throwable());
        return;
      }
      mySetFilter = true;
      setOurFilter(filter);
    }
  }

  private static void setOurFilter(@NotNull Condition<? super IdeaPluginDescriptor> filter) {
    ourFilter = filter;
    reapplyFilter();
  }

  public static void reapplyFilter() {
    synchronized (ourLock) {
      syncFilteredUpdates();

      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(null);
      }

      Integer countValue = getCount();
      for (PluginUpdatesService service : SERVICES) {
        service.runAllCallbacks(countValue);
      }
    }
  }

  public void dispose() {
    synchronized (ourLock) {
      dispose(this);
      if (mySetFilter) {
        setOurFilter(DEFAULT_FILTER);
        mySetFilter = false;
      }
    }
  }

  private static void dispose(@NotNull PluginUpdatesService service) {
    synchronized (ourLock) {
      SERVICES.remove(service);

      if (SERVICES.isEmpty()) {
        setAllUpdates(null);
        ourPrepared = false;
        ourPreparing = false;
      }
    }
  }

  public static boolean isNeedUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();

    synchronized (ourLock) {
      if (ourPrepared && ourFilteredUpdates != null) {
        for (IdeaPluginDescriptor downloader : ourFilteredUpdates) {
          if (pluginId.equals(downloader.getPluginId())) {
            return true;
          }
        }
      }
    }

    return InstalledPluginsState.getInstance().hasNewerVersion(pluginId);
  }

  public static @Nullable Collection<IdeaPluginDescriptor> getUpdates() {
    synchronized (ourLock) {
      return !ourPrepared || ourPreparing || ourFilteredUpdates == null ? null : ourFilteredUpdates;
    }
  }

  public static @Nullable @Nls String getUpdatesTooltip() {
    Collection<IdeaPluginDescriptor> updates = getUpdates();
    if (ContainerUtil.isEmpty(updates)) {
      return null;
    }
    return IdeBundle.message("updates.plugin.ready.tooltip", StringUtil.join(updates, plugin -> plugin.getName(), ", "), updates.size());
  }

  private static void calculateUpdates() {
    synchronized (ourLock) {
      if (ourPreparing) {
        return;
      }
      ourPreparing = true;
      setAllUpdates(null);
    }

    // for example, if executed as part of Traverse UI - don't wait check updates
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    NonUrgentExecutor.getInstance().execute(() -> {
      List<IdeaPluginDescriptor> cache = ContainerUtil.map(UpdateChecker.getInternalPluginUpdates().getPluginUpdates().getAll(),
                                                           PluginDownloader::getDescriptor);

      ApplicationManager.getApplication().invokeLater(() -> {
        synchronized (ourLock) {
          ourPreparing = false;

          if (ourReset) {
            ourReset = false;
            calculateUpdates();
            return;
          }

          ourPrepared = true;
          setAllUpdates(cache);

          Integer countValue = getCount();
          for (PluginUpdatesService service : SERVICES) {
            service.runAllCallbacks(countValue);
          }
        }
      }, ModalityState.any());
    });
  }

  private void runAllCallbacks(@Nullable Integer countValue) {
    runCountCallbacks(countValue);

    if (myUpdateCallbacks != null) {
      for (var callback : myUpdateCallbacks) {
        callback.accept(countValue == null ? null : ourFilteredUpdates);
      }
    }
  }

  private void runCountCallbacks(@Nullable Integer countValue) {
    if (myCountCallback != null) {
      myCountCallback.accept(countValue);
    }
  }

  private static @Nullable Integer getCount() {
    return ourFilteredUpdates == null ? null : ourFilteredUpdates.size();
  }

  private static void setAllUpdates(@Nullable Collection<IdeaPluginDescriptor> updates) {
    ourAllUpdates = updates;
    syncFilteredUpdates();
  }

  private static void syncFilteredUpdates() {
    ourFilteredUpdates = ourAllUpdates == null ? null : ContainerUtil.filter(ourAllUpdates, ourFilter);
  }
}
