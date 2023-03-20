// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginStateListener;
import com.intellij.ide.plugins.PluginStateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
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
  private static final List<PluginUpdatesService> SERVICES = new ArrayList<>();
  private static final Object ourLock = new Object();
  private static Collection<IdeaPluginDescriptor> myCache;
  private static boolean myPrepared;
  private static boolean myPreparing;
  private static boolean myReset;

  private Consumer<? super Integer> myCountCallback;
  private Consumer<? super Collection<IdeaPluginDescriptor>> myUpdateCallback;

  static {
    PluginStateManager.addStateListener(new PluginStateListener() {
      @Override
      public void install(@NotNull IdeaPluginDescriptor descriptor) {
        finishUpdate(descriptor);
      }

      @Override
      public void uninstall(@NotNull IdeaPluginDescriptor descriptor) {
        install(descriptor);
      }
    });
  }

  public static @NotNull PluginUpdatesService connectWithCounter(@NotNull Consumer<? super Integer> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myCountCallback = callback;

    synchronized (ourLock) {
      SERVICES.add(service);

      if (myPrepared) {
        callback.accept(getCount());
        return service;
      }
    }

    calculateUpdates();
    return service;
  }

  @NotNull
  public static PluginUpdatesService connectWithUpdates(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    PluginUpdatesService service = new PluginUpdatesService();
    service.myUpdateCallback = callback;

    synchronized (ourLock) {
      SERVICES.add(service);

      if (myPrepared) {
        callback.accept(myCache);
      }
    }

    return service;
  }

  public void calculateUpdates(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    synchronized (ourLock) {
      myUpdateCallback = callback;

      if (myPrepared) {
        callback.accept(myCache);
        return;
      }
    }
    calculateUpdates();
  }

  private static void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    synchronized (ourLock) {
      if (!myPrepared || myCache == null) {
        return;
      }

      for (Iterator<IdeaPluginDescriptor> I = myCache.iterator(); I.hasNext(); ) {
        IdeaPluginDescriptor downloadedDescriptor = I.next();

        if (Objects.equals(downloadedDescriptor.getPluginId(), descriptor.getPluginId())) {
          I.remove();

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
      if (!myPrepared || myCache == null) {
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

      if (myPreparing) {
        resetUpdates();
      }
      else {
        calculateUpdates();
      }
    }
  }

  private static void resetUpdates() {
    myReset = true;
  }

  public void dispose() {
    dispose(this);
  }

  private static void dispose(@NotNull PluginUpdatesService service) {
    synchronized (ourLock) {
      SERVICES.remove(service);

      if (SERVICES.isEmpty()) {
        myCache = null;
        myPrepared = false;
        myPreparing = false;
      }
    }
  }

  public static boolean isNeedUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    PluginId pluginId = descriptor.getPluginId();

    synchronized (ourLock) {
      if (myPrepared && myCache != null) {
        for (IdeaPluginDescriptor downloader : myCache) {
          if (pluginId.equals(downloader.getPluginId())) {
            return true;
          }
        }
      }
    }

    return InstalledPluginsState.getInstance().hasNewerVersion(pluginId);
  }

  @Nullable
  public static Collection<IdeaPluginDescriptor> getUpdates() {
    synchronized (ourLock) {
      return !myPrepared || myPreparing || myCache == null ? null : myCache;
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
      if (myPreparing) {
        return;
      }
      myPreparing = true;
      myCache = null;
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
          myPreparing = false;

          if (myReset) {
            myReset = false;
            calculateUpdates();
            return;
          }

          myPrepared = true;
          myCache = cache;

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

    if (myUpdateCallback != null) {
      myUpdateCallback.accept(countValue == null ? null : myCache);
    }
  }

  private void runCountCallbacks(@Nullable Integer countValue) {
    if (myCountCallback != null) {
      myCountCallback.accept(countValue);
    }
  }

  @Nullable
  private static Integer getCount() {
    return myCache == null ? null : myCache.size();
  }
}
