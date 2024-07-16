// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** @deprecated please implement {@link com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider} instead. */
@Deprecated(forRemoval = true)
@SuppressWarnings("DeprecatedIsStillUsed")
public interface CustomPluginRepoContributor {
  ExtensionPointName<CustomPluginRepoContributor> EP_NAME = ExtensionPointName.create("com.intellij.customPluginRepoContributor");

  @NotNull List<String> getRepoUrls();

  @ApiStatus.Internal
  static @NotNull List<String> getRepositoriesFromContributors() {
    var result = new ArrayList<String>();
    EP_NAME.forEachExtensionSafe(provider -> result.addAll(provider.getRepoUrls()));
    return result;
  }
}
