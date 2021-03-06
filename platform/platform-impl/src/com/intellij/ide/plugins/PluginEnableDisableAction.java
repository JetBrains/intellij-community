// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnableDisableAction {

  ENABLE_GLOBALLY(
    "plugins.configurable.enable.for.all.projects",
    PluginEnabledState.ENABLED,
    true
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state != PluginEnabledState.ENABLED;
    }
  },
  ENABLE_FOR_PROJECT(
    "plugins.configurable.enable.for.current.project",
    PluginEnabledState.ENABLED_FOR_PROJECT,
    true
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return !state.isEnabled();
    }
  },
  ENABLE_FOR_PROJECT_DISABLE_GLOBALLY(
    "plugins.configurable.enable.for.current.project.only",
    PluginEnabledState.ENABLED_FOR_PROJECT,
    false
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state == PluginEnabledState.ENABLED;
    }
  },
  DISABLE_GLOBALLY(
    "plugins.configurable.disable.for.all.projects",
    PluginEnabledState.DISABLED,
    false
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state != PluginEnabledState.DISABLED;
    }
  },
  DISABLE_FOR_PROJECT(
    "plugins.configurable.disable.for.current.project",
    PluginEnabledState.DISABLED_FOR_PROJECT,
    false
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state.isEnabled();
    }
  },
  DISABLE_FOR_PROJECT_ENABLE_GLOBALLY(
    "plugins.configurable.disable.for.current.project.only",
    PluginEnabledState.DISABLED_FOR_PROJECT,
    true
  ) {
    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return false;
    }
  };

  private final @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String myPropertyKey;
  private final @NotNull PluginEnabledState myState;
  private final boolean myIsEnable;

  PluginEnableDisableAction(@NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String propertyKey,
                            @NotNull PluginEnabledState state,
                            boolean isEnable) {
    myPropertyKey = propertyKey;
    myState = state;
    myIsEnable = isEnable;
  }

  public abstract boolean isApplicable(@NotNull PluginEnabledState state);

  public @Nullable PluginEnabledState apply(@NotNull PluginEnabledState state) {
    return isApplicable(state) ? myState : null;
  }

  public boolean isPerProject() {
    return myState.isPerProject();
  }

  public boolean isEnable() {
    return myIsEnable;
  }

  public boolean isDisable() {
    return !isEnable();
  }

  @Override
  public @NotNull @Nls String toString() {
    return IdeBundle.message(myPropertyKey);
  }

  public static @NotNull PluginEnableDisableAction globally(boolean enable) {
    return enable ? ENABLE_GLOBALLY : DISABLE_GLOBALLY;
  }
}
