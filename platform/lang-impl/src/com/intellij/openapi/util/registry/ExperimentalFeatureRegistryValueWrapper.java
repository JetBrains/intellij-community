// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.registry;

import com.intellij.openapi.application.ExperimentalFeature;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
final class ExperimentalFeatureRegistryValueWrapper extends RegistryValue {
  private final ExperimentalFeature feature;

  ExperimentalFeatureRegistryValueWrapper(@NotNull ExperimentalFeature feature) {
    super(Registry.getInstance(), feature.id, null);

    this.feature = feature;
  }

  @Override
  public @NotNull String asString() {
    return Boolean.toString(asBoolean());
  }

  @Override
  public boolean asBoolean() {
    return Experiments.getInstance().isFeatureEnabled(feature.id);
  }

  @Override
  public boolean isRestartRequired() {
    return feature.requireRestart;
  }

  @Override
  public boolean isChangedFromDefault() {
    return Experiments.getInstance().isChanged(feature.id);
  }

  @Override
  public boolean isBoolean() {
    return true;
  }

  @Override
  public void setValue(@NotNull String value) {
    boolean enable = Boolean.parseBoolean(value);
    Experiments.getInstance().setFeatureEnabled(feature.id, enable);
  }

  @Override
  public @NotNull String getDescription() {
    return StringUtil.notNullize(feature.description);
  }
}
