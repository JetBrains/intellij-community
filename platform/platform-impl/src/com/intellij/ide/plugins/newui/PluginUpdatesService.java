// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.ui.AncestorListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public class PluginUpdatesService {
  private static boolean myCreate = true;
  private static Consumer<Integer> myTreeCallback;
  private static Consumer<Integer> myTabCallback;
  private static List<Consumer<Collection<PluginDownloader>>> myPanelCallbacks;
  private static Collection<PluginDownloader> myCache;
  private static boolean myPrepared;
  private static boolean myPreparing;

  public static void connectTreeRenderer(@NotNull JComponent uiForDispose, @NotNull Consumer<Integer> callback) {
    assert SwingUtilities.isEventDispatchThread();

    if (myCreate) {
      myCreate = false;
      uiForDispose.addAncestorListener(new AncestorListenerAdapter() {
        @Override
        public void ancestorRemoved(AncestorEvent event) {
          dispose();
        }
      });
    }
    myTreeCallback = callback;
    handleCount(callback);
  }

  @Nullable
  public static Runnable connectConfigurable(@NotNull Consumer<Integer> callback) {
    assert SwingUtilities.isEventDispatchThread();

    Runnable disposer = myCreate ? () -> dispose() : null;
    myCreate = false;
    myTabCallback = callback;
    handleCount(callback);
    return disposer;
  }

  public static void calculateUpdates(@NotNull Consumer<Collection<PluginDownloader>> callback) {
    assert SwingUtilities.isEventDispatchThread();

    if (myPrepared) {
      callback.accept(myCache);
      return;
    }
    if (myPanelCallbacks == null) {
      myPanelCallbacks = new ArrayList<>();
    }
    myPanelCallbacks.add(callback);
    calculateUpdates();
  }

  public static void finishUpdate(@NotNull IdeaPluginDescriptor descriptor) {
    assert SwingUtilities.isEventDispatchThread();

    if (!myPrepared || myCache == null) {
      return;
    }

    for (Iterator<PluginDownloader> I = myCache.iterator(); I.hasNext(); ) {
      PluginDownloader downloader = I.next();
      if (downloader.getDescriptor() == descriptor) {
        I.remove();
        runCallbacks(getCount());
        return;
      }
    }
  }

  public static void recalculateUpdates() {
    assert SwingUtilities.isEventDispatchThread();
    assert !myPreparing;

    runCallbacks(-1);
    calculateUpdates();
  }

  private static void dispose() {
    assert SwingUtilities.isEventDispatchThread();

    myCreate = true;
    myTreeCallback = null;
    myTabCallback = null;
    myPanelCallbacks = null;
    myCache = null;
    myPrepared = false;
    myPreparing = false;
  }

  private static void handleCount(@NotNull Consumer<Integer> callback) {
    assert SwingUtilities.isEventDispatchThread();

    if (myPrepared) {
      callback.accept(getCount());
    }
    else {
      calculateUpdates();
    }
  }

  private static void calculateUpdates() {
    assert SwingUtilities.isEventDispatchThread();

    if (myPreparing) {
      return;
    }
    myPreparing = true;
    myCache = null;

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Collection<PluginDownloader> updates = UpdateChecker.getPluginUpdates();

      ApplicationManager.getApplication().invokeLater(() -> {
        assert SwingUtilities.isEventDispatchThread();

        myPreparing = false;
        myPrepared = true;
        myCache = updates;

        runCallbacks(getCount());

        if (myPanelCallbacks != null) {
          for (Consumer<Collection<PluginDownloader>> callback : myPanelCallbacks) {
            callback.accept(myCache);
          }
          myPanelCallbacks = null;
        }
      }, ModalityState.any());
    });
  }

  @Nullable
  private static Integer getCount() {
    return myCache == null ? null : new Integer(myCache.size());
  }

  private static void runCallbacks(@Nullable Integer countValue) {
    if (myTreeCallback != null) {
      myTreeCallback.accept(countValue);
    }
    if (myTabCallback != null) {
      myTabCallback.accept(countValue);
    }
  }
}