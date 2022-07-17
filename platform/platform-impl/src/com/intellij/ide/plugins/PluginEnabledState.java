// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum PluginEnabledState {

  ENABLED {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return DynamicPluginEnabler.isPerProjectEnabled() ? "plugins.configurable.enabled.for.all.projects" : "plugins.configurable.enabled";
    }
  },
  ENABLED_FOR_PROJECT {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.enabled.for.current.project";
    }
  },
  DISABLED {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return DynamicPluginEnabler.isPerProjectEnabled() ? "plugins.configurable.disabled.for.all.projects" : "plugins.configurable.disabled";
    }
  },
  DISABLED_FOR_PROJECT {
    @Override
    protected @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey() {
      return "plugins.configurable.disabled.for.current.project";
    }
  };

  protected abstract @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String getPropertyKey();

  public @NotNull @Nls String getPresentableText() {
    return IdeBundle.message(getPropertyKey());
  }

  public boolean isEnabled() {
    return this == ENABLED || this == ENABLED_FOR_PROJECT;
  }

  public boolean isDisabled() {
    return this == DISABLED || this == DISABLED_FOR_PROJECT;
  }

  public boolean isPerProject() {
    return this == ENABLED_FOR_PROJECT || this == DISABLED_FOR_PROJECT;
  }

  public @NotNull PluginEnabledState getInverted() {
    return getState(isDisabled(), isPerProject());
  }

  public static @NotNull PluginEnabledState getState(boolean enabled, boolean perProject) {
    return perProject ? enabled ? ENABLED_FOR_PROJECT : DISABLED_FOR_PROJECT : globally(enabled);
  }

  public static @NotNull PluginEnabledState globally(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }
}
