// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnabledState {

  @ApiStatus.Experimental
  ENABLED_ON_DEMAND(true, "plugins.configurable.enabled.on.demand"),
  ENABLED(true, "plugins.configurable.enabled"),
  DISABLED(false, "plugins.configurable.disabled");

  private final boolean myIsEnabled;
  private final @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String myPropertyKey;

  PluginEnabledState(boolean isEnabled, @NotNull String propertyKey) {
    myIsEnabled = isEnabled;
    myPropertyKey = propertyKey;
  }

  public @NotNull @Nls String getPresentableText() {
    return IdeBundle.message(myPropertyKey);
  }

  public boolean isEnabled() {
    return myIsEnabled;
  }

  public boolean isDisabled() {
    return !isEnabled();
  }

  @ApiStatus.Experimental
  public static @NotNull PluginEnabledState getState(boolean isEnabled,
                                                     boolean isOnDemand) {
    return isEnabled ? isOnDemand ? ENABLED_ON_DEMAND : ENABLED : DISABLED;
  }
}
