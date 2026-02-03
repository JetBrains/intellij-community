// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
@ApiStatus.Internal
@State(
  name = "LineMarkerSettings",
  storages = @Storage("gutter.xml"),
  category = SettingsCategory.CODE
)
public final class LineMarkerSettingsImpl extends LineMarkerSettings implements PersistentStateComponent<LineMarkerSettingsImpl> {

  @Override
  public boolean isEnabled(@NotNull GutterIconDescriptor descriptor) {
    Boolean aBoolean = providers.get(descriptor.getId());
    if (aBoolean == null) return descriptor.isEnabledByDefault();
    return aBoolean;
  }

  @Override
  public void setEnabled(@NotNull GutterIconDescriptor descriptor, boolean selected) {
    providers.put(descriptor.getId(), selected);
  }

  public void resetEnabled(@NotNull GutterIconDescriptor descriptor) {
    providers.remove(descriptor.getId());
  }

  @MapAnnotation
  public Map<String, Boolean> providers = new HashMap<>();

  @Override
  public @Nullable LineMarkerSettingsImpl getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull LineMarkerSettingsImpl state) {
    providers.clear();
    providers.putAll(state.providers);
  }
}
