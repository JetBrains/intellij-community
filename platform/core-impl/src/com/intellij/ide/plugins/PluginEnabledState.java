// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.core.CoreBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnabledState {

  ENABLED("plugins.configurable.enabled.for.all.projects",
          true,
          false),
  ENABLED_FOR_PROJECT("plugins.configurable.enabled.for.current.project",
                      true,
                      true),
  DISABLED("plugins.configurable.disabled.for.all.projects",
           false,
           false),
  DISABLED_FOR_PROJECT("plugins.configurable.disabled.for.current.project",
                       false,
                       true);

  private final @NotNull @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String myPropertyKey;
  private final boolean myEnabled;
  private final boolean myPerProject;

  PluginEnabledState(@NotNull @PropertyKey(resourceBundle = CoreBundle.BUNDLE) String propertyKey,
                     boolean enabled,
                     boolean perProject) {
    myPropertyKey = propertyKey;
    myEnabled = enabled;
    myPerProject = perProject;
  }

  public @NotNull @Nls String toString() {
    return CoreBundle.message(myPropertyKey);
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public boolean isDisabled() {
    return !myEnabled;
  }

  public boolean isPerProject() {
    return myPerProject;
  }

  public @NotNull PluginEnabledState getInverted() {
    return getState(!myEnabled, isPerProject());
  }

  public static @NotNull PluginEnabledState getState(boolean enabled,
                                                     boolean perProject) {
    for (PluginEnabledState value : values()) {
      if (value.myEnabled == enabled &&
          value.myPerProject == perProject) {
        return value;
      }
    }

    throw new IllegalArgumentException("Target state not found: enabled='" + enabled + "', perProject='" + perProject + "'");
  }

  public static @NotNull PluginEnabledState globally(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }
}
