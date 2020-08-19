// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginUpdatesService {
  private static final List<PluginUpdatesService> SERVICES = new ArrayList<>();
  private static Collection<IdeaPluginDescriptor> myCache;
  private static boolean myPrepared;
  private static boolean myPreparing;
  private static boolean myReset;

  private Consumer<? super Integer> myTreeCallback;
  private Consumer<? super Integer> myTabCallback;
  private Consumer<? super Collection<IdeaPluginDescriptor>> myInstalledPanelCallback;
  private Consumer<? super Collection<IdeaPluginDescriptor>> myUpdatePanelCallback;

  @NotNull
  public static PluginUpdatesService connectTreeRenderer(@NotNull Consumer<? super Integer> callback) {
    checkAccess();

    PluginUpdatesService service = new PluginUpdatesService();
    SERVICES.add(service);
    service.myTreeCallback = callback;

    if (myPrepared) {
      callback.accept(getCount());
    }
    else {
      calculateUpdates();
    }

    return service;
  }

  @NotNull
  public static PluginUpdatesService connectConfigurable(@NotNull Consumer<? super Integer> callback) {
    checkAccess();

    PluginUpdatesService service = new PluginUpdatesService();
    SERVICES.add(service);
    service.myTabCallback = callback;

    if (myPrepared) {
      callback.accept(getCount());
    }
    else {
      calculateUpdates();
    }

    return service;
  }

  public void connectInstalled(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    checkAccess();
    myInstalledPanelCallback = callback;

    if (myPrepared) {
      callback.accept(myCache);
    }
    else {
      calculateUpdates();
    }
  }

  public void calculateUpdates(@NotNull Consumer<? super Collection<IdeaPluginDescriptor>> callback) {
    checkAccess();
    myUpdatePanelCallback = callback;

    if (myPrepared) {
      callback.accept(myCache);
    }
    else {
      calculateUpdates();
    }
  }

  public void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    checkAccess();

    if (!myPrepared || myCache == null) {
      return;
    }

    for (Iterator<IdeaPluginDescriptor> I = myCache.iterator(); I.hasNext(); ) {
      IdeaPluginDescriptor downloadedDescriptor = I.next();

      if (downloadedDescriptor.equals(descriptor)) {
        I.remove();

        Integer countValue = getCount();
        for (PluginUpdatesService service : SERVICES) {
          service.runCountCallbacks(countValue);
        }

        return;
      }
    }
  }

  public void finishUpdate() {
    checkAccess();

    if (!myPrepared || myCache == null) {
      return;
    }

    Integer countValue = getCount();
    for (PluginUpdatesService service : SERVICES) {
      service.runCountCallbacks(countValue);
    }
  }

  public void recalculateUpdates() {
    checkAccess();

    for (PluginUpdatesService service : SERVICES) {
      service.runAllCallbacks(0);
    }

    if (myPreparing) {
      resetUpdates();
    }
    else {
      calculateUpdates();
    }
  }

  private static void resetUpdates() {
    myReset = true;
  }

  public void dispose() {
    checkAccess();
    dispose(this);
  }

  private static void dispose(@NotNull PluginUpdatesService service) {
    SERVICES.remove(service);

    if (SERVICES.isEmpty()) {
      myCache = null;
      myPrepared = false;
      myPreparing = false;
    }
  }

  public static boolean isNeedUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    checkAccess();

    PluginId pluginId = descriptor.getPluginId();
    if (myPrepared && myCache != null) {
      for (IdeaPluginDescriptor downloader : myCache) {
        if (pluginId.equals(downloader.getPluginId())) {
          return true;
        }
      }
    }

    return InstalledPluginsState.getInstance().hasNewerVersion(pluginId);
  }

  @Nullable
  public static Collection<IdeaPluginDescriptor> getUpdates() {
    checkAccess();
    return !myPrepared || myPreparing || myCache == null ? null : myCache;
  }

  private static void calculateUpdates() {
    if (myPreparing) {
      return;
    }
    myPreparing = true;
    myCache = null;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      UpdateChecker.CheckPluginsUpdateResult updates = UpdateChecker.checkPluginsUpdate(new EmptyProgressIndicator());

      ApplicationManager.getApplication().invokeLater(() -> {
        checkAccess();

        myPreparing = false;

        if (myReset) {
          myReset = false;
          calculateUpdates();
          return;
        }

        myPrepared = true;
        List<IdeaPluginDescriptor> cache = new ArrayList<>();
        Collection<PluginDownloader> availableUpdates = updates.getAvailableUpdates();
        if (availableUpdates != null) {
          cache.addAll(ContainerUtil.map(availableUpdates, (downloader -> downloader.getDescriptor())));
        }
        cache.addAll(ContainerUtil.map(updates.getAvailableDisabledUpdates(), (downloader -> downloader.getDescriptor())));
        myCache = cache;

        Integer countValue = getCount();
        for (PluginUpdatesService service : SERVICES) {
          service.runAllCallbacks(countValue);
        }
      }, ModalityState.any());
    });
  }

  private void runAllCallbacks(@Nullable Integer countValue) {
    runCountCallbacks(countValue);

    if (myInstalledPanelCallback != null) {
      myInstalledPanelCallback.accept(myCache);
    }
    if (myUpdatePanelCallback != null) {
      myUpdatePanelCallback.accept(myCache);
    }
  }

  private void runCountCallbacks(@Nullable Integer countValue) {
    if (myTreeCallback != null) {
      myTreeCallback.accept(countValue);
    }
    if (myTabCallback != null) {
      myTabCallback.accept(countValue);
    }
  }

  @Nullable
  private static Integer getCount() {
    return myCache == null ? null : myCache.size();
  }

  private static void checkAccess() {
    assert SwingUtilities.isEventDispatchThread();
  }
}