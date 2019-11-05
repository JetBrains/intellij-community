// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
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
  private static Collection<PluginDownloader> myCache;
  private static boolean myPrepared;
  private static boolean myPreparing;
  private static boolean myReset;

  private Consumer<Integer> myTreeCallback;
  private Consumer<Integer> myTabCallback;
  private Consumer<? super Collection<PluginDownloader>> myInstalledPanelCallback;
  private Consumer<? super Collection<PluginDownloader>> myUpdatePanelCallback;

  @NotNull
  public static PluginUpdatesService connectTreeRenderer(@NotNull Consumer<Integer> callback) {
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
  public static PluginUpdatesService connectConfigurable(@NotNull Consumer<Integer> callback) {
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

  public void connectInstalled(@NotNull Consumer<? super Collection<PluginDownloader>> callback) {
    checkAccess();
    myInstalledPanelCallback = callback;

    if (myPrepared) {
      callback.accept(myCache);
    }
    else {
      calculateUpdates();
    }
  }

  public void calculateUpdates(@NotNull Consumer<? super Collection<PluginDownloader>> callback) {
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

    for (Iterator<PluginDownloader> I = myCache.iterator(); I.hasNext(); ) {
      PluginDownloader downloader = I.next();

      if (downloader.getDescriptor() == descriptor) {
        I.remove();

        Integer countValue = getCount();
        for (PluginUpdatesService service : SERVICES) {
          service.runCountCallbacks(countValue);
        }

        return;
      }
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
      for (PluginDownloader downloader : myCache) {
        if (pluginId == downloader.getId()) {
          return true;
        }
      }
    }

    return InstalledPluginsState.getInstance().hasNewerVersion(pluginId);
  }

  @Nullable
  public static Collection<PluginDownloader> getUpdates() {
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
      Collection<PluginDownloader> updates = UpdateChecker.getPluginUpdates();

      ApplicationManager.getApplication().invokeLater(() -> {
        checkAccess();

        myPreparing = false;

        if (myReset) {
          myReset = false;
          calculateUpdates();
          return;
        }

        myPrepared = true;
        myCache = updates;

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