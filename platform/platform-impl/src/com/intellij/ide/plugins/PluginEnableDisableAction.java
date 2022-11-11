// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnableDisableAction {

  ENABLE_GLOBALLY(PluginEnabledState.ENABLED, "plugins.configurable.enable"),
  DISABLE_GLOBALLY(PluginEnabledState.DISABLED, "plugins.configurable.disable");

  private final @NotNull PluginEnabledState myState;
  private final @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String myPropertyKey;

  PluginEnableDisableAction(@NotNull PluginEnabledState state,
                            @NotNull String propertyKey) {
    myState = state;
    myPropertyKey = propertyKey;
  }

  public boolean isApplicable(@NotNull PluginEnabledState state) {
    return state != myState;
  }

  public @Nullable PluginEnabledState apply(@NotNull PluginEnabledState state) {
    return isApplicable(state) ? myState : null;
  }


  public boolean isEnable() {
    return myState.isEnabled();
  }

  public boolean isDisable() {
    return myState.isDisabled();
  }

  public @NotNull @Nls String getPresentableText() {
    return IdeBundle.message(myPropertyKey);
  }

  public static @NotNull PluginEnableDisableAction globally(boolean enable) {
    return enable ? ENABLE_GLOBALLY : DISABLE_GLOBALLY;
  }
}
