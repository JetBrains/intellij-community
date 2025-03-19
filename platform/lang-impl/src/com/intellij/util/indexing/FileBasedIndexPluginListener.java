// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.indexing.dependencies.IndexingDependenciesFingerprint;
import org.jetbrains.annotations.NotNull;

final class FileBasedIndexPluginListener implements DynamicPluginListener {
  private final @NotNull FileBasedIndexTumbler mySwitcher;

  FileBasedIndexPluginListener() {
    mySwitcher = new FileBasedIndexTumbler("Plugin loaded/unloaded");
  }

  @Override
  public void beforePluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    beforePluginSetChanged();
  }

  @Override
  public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    beforePluginSetChanged();
  }

  @Override
  public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
    afterPluginSetChanged();
  }

  @Override
  public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    afterPluginSetChanged();
  }

  private void beforePluginSetChanged() {
    mySwitcher.turnOff();
    ApplicationManager.getApplication().getService(IndexingDependenciesFingerprint.class).resetCache();
  }

  private void afterPluginSetChanged() {
    // we don't use dedicated listener for IndexingDependenciesFingerprint, because order is important: first invalidate, then scan.
    ApplicationManager.getApplication().getService(IndexingDependenciesFingerprint.class).resetCache();
    mySwitcher.turnOn(null);
  }
}
