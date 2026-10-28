// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint;
import org.jetbrains.annotations.NotNull;

final class FileBasedIndexPluginListener implements DynamicPluginListener {
  private static final Logger LOG = Logger.getInstance(FileBasedIndexPluginListener.class);
  private static final String SKIP_INDEX_RELOAD_PROPERTY = "intellij.indexes.skip.reload.on.plugin.load.unload";

  private final @NotNull FileBasedIndexTumbler mySwitcher;
  /** The number of `before` events whose `after` event has not arrived yet. */
  private int myPendingTurnOnCount;

  FileBasedIndexPluginListener() {
    mySwitcher = new FileBasedIndexTumbler("Plugin loaded/unloaded");
  }


  @Override
  public void beforePluginsLoaded() {
    beforePluginSetChanged();
  }

  @Override
  public void pluginsLoaded() {
    afterPluginSetChanged();
  }


  @Override
  public void beforePluginsUnloaded() {
    beforePluginSetChanged();
  }

  @Override
  public void pluginsUnloaded() {
    afterPluginSetChanged();
  }


  private void beforePluginSetChanged() {
    if (!isIndexReloadSkippedInTests()) {
      try {
        mySwitcher.turnOff();
      }
      finally {
        // the tumbler counts a failed turn off too, so the next `after` event must turn the index on
        myPendingTurnOnCount++;
      }
    }
    ApplicationManager.getApplication().getService(IndexingDependenciesFingerprint.class).resetCache();
  }

  private void afterPluginSetChanged() {
    // we don't use dedicated listener for IndexingDependenciesFingerprint, because order is important: first invalidate, then scan.
    ApplicationManager.getApplication().getService(IndexingDependenciesFingerprint.class).resetCache();
    if (isIndexReloadSkippedInTests()) {
      return;
    }
    if (myPendingTurnOnCount == 0) {
      // The plugin set change that this event completes has loaded this listener, so the listener has not turned the index off.
      // The index extensions of the modules that loaded with it register on the next turn off and turn on of the index.
      LOG.info("The index was not turned off by this listener, skip turning it on");
      return;
    }
    myPendingTurnOnCount--;
    mySwitcher.turnOn();
  }

  private static boolean isIndexReloadSkippedInTests() {
    return ApplicationManager.getApplication().isUnitTestMode() && Boolean.getBoolean(SKIP_INDEX_RELOAD_PROPERTY);
  }
}
