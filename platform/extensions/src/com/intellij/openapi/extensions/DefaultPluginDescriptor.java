// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.extensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Date;

public final class DefaultPluginDescriptor implements PluginDescriptor {
  private final @NotNull PluginId myPluginId;
  private final ClassLoader myPluginClassLoader;

  public DefaultPluginDescriptor(@NotNull String pluginId) {
    myPluginId = PluginId.getId(pluginId);
    myPluginClassLoader = null;
  }

  public DefaultPluginDescriptor(@NotNull PluginId pluginId) {
    myPluginId = pluginId;
    myPluginClassLoader = null;
  }

  public DefaultPluginDescriptor(@NotNull PluginId pluginId, @Nullable ClassLoader pluginClassLoader) {
    myPluginId = pluginId;
    myPluginClassLoader = pluginClassLoader;
  }

  @Override
  public @NotNull PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public ClassLoader getPluginClassLoader() {
    return myPluginClassLoader;
  }

  @Override
  public Path getPluginPath() {
    return null;
  }

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public String getChangeNotes() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public @Nullable String getProductCode() {
    return null;
  }

  @Override
  public @Nullable Date getReleaseDate() {
    return null;
  }

  @Override
  public boolean isLicenseOptional() {
    return false;
  }

  @Override
  public int getReleaseVersion() {
    return 0;
  }

  @Override
  public PluginId @NotNull [] getOptionalDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  @Override
  public String getVendor() {
    return null;
  }

  @Override
  public String getVersion() {
    return null;
  }

  @Override
  public String getResourceBundleBaseName() {
    return null;
  }

  @Override
  public String getCategory() {
    return null;
  }

  @Override
  public String getVendorEmail() {
    return null;
  }

  @Override
  public String getVendorUrl() {
    return null;
  }

  @Override
  public String getUrl() {
    return null;
  }

  @Override
  public String getSinceBuild() {
    return null;
  }

  @Override
  public String getUntilBuild() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setEnabled(boolean enabled) {
  }
}
