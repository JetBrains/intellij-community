// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

@Internal
public final class ConfigImportOptions {
  public final Logger log;

  public boolean headless;
  public @Nullable ConfigImportSettings importSettings;
  public @Nullable BuildNumber compatibleBuildNumber;
  public Path bundledPluginPath = null;
  public boolean mergeVmOptions = false;
  public MarketplacePluginDownloadService downloadService;
  public @Nullable ProgressIndicator headlessProgressIndicator = null;

  public ConfigImportOptions(Logger log) {
    this.log = log;
  }
}
