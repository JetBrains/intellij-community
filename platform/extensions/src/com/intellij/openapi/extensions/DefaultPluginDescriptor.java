// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public @Nullable String getChangeNotes() {
    return null;
  }

  @Override
  public @Nullable String getName() {
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
  public @Nullable String getVendor() {
    return null;
  }

  @Override
  public @Nullable String getVersion() {
    return null;
  }

  @Override
  public @Nullable String getResourceBundleBaseName() {
    return null;
  }

  @Override
  public @Nullable String getCategory() {
    return null;
  }

  @Override
  public @Nullable String getVendorEmail() {
    return null;
  }

  @Override
  public @Nullable String getVendorUrl() {
    return null;
  }

  @Override
  public @Nullable String getUrl() {
    return null;
  }

  @Override
  public @Nullable String getSinceBuild() {
    return null;
  }

  @Override
  public @Nullable String getUntilBuild() {
    return null;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setEnabled(boolean enabled) { }

  @Override
  public String toString() {
    return "Default plugin descriptor for " + myPluginId;
  }
}
