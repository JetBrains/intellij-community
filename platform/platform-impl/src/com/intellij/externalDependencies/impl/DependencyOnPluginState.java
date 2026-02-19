// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl;

import com.intellij.externalDependencies.DependencyOnPlugin;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Tag("plugin")
public final class DependencyOnPluginState {
  DependencyOnPluginState() {
  }

  DependencyOnPluginState(DependencyOnPlugin dependency) {
    myId = dependency.getPluginId();
    myMinVersion = dependency.getRawMinVersion();
    myMaxVersion = dependency.getRawMaxVersion();
  }

  @Attribute("id")
  public String myId;
  @Attribute("min-version")
  public String myMinVersion;
  @Attribute("max-version")
  public String myMaxVersion;
}
