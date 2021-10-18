// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnableDisableAction {

  ENABLE_GLOBALLY(PluginEnabledState.ENABLED, true) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return DynamicPluginEnabler.isPerProjectEnabled() ? "plugins.configurable.enable.for.all.projects" : "plugins.configurable.enable";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state != PluginEnabledState.ENABLED;
    }
  },
  ENABLE_FOR_PROJECT(PluginEnabledState.ENABLED_FOR_PROJECT, true) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.enable.for.current.project";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return DynamicPluginEnabler.isPerProjectEnabled() && !state.isEnabled();
    }
  },
  ENABLE_FOR_PROJECT_DISABLE_GLOBALLY(PluginEnabledState.ENABLED_FOR_PROJECT, false) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.enable.for.current.project.only";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return DynamicPluginEnabler.isPerProjectEnabled() && state == PluginEnabledState.ENABLED;
    }
  },
  DISABLE_GLOBALLY(PluginEnabledState.DISABLED, false) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return DynamicPluginEnabler.isPerProjectEnabled() ? "plugins.configurable.disable.for.all.projects" : "plugins.configurable.disable";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return state != PluginEnabledState.DISABLED;
    }
  },
  DISABLE_FOR_PROJECT(PluginEnabledState.DISABLED_FOR_PROJECT, false) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.disable.for.current.project";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return DynamicPluginEnabler.isPerProjectEnabled() && state.isEnabled();
    }
  },
  DISABLE_FOR_PROJECT_ENABLE_GLOBALLY(PluginEnabledState.DISABLED_FOR_PROJECT, true) {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.disable.for.current.project.only";
    }

    @Override
    public boolean isApplicable(@NotNull PluginEnabledState state) {
      return false;
    }
  };

  private final @NotNull PluginEnabledState myState;
  private final boolean myIsEnable;

  PluginEnableDisableAction(@NotNull PluginEnabledState state, boolean isEnable) {
    myState = state;
    myIsEnable = isEnable;
  }

  public abstract boolean isApplicable(@NotNull PluginEnabledState state);

  protected abstract @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey();

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

  public @NotNull @Nls String getPresentableText() {
    return IdeBundle.message(getPropertyKey());
  }

  public static @NotNull PluginEnableDisableAction globally(boolean enable) {
    return enable ? ENABLE_GLOBALLY : DISABLE_GLOBALLY;
  }
}
