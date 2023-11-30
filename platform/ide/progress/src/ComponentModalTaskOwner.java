// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

@Internal
public final class ComponentModalTaskOwner implements ModalTaskOwner {

  private final Component component;

  ComponentModalTaskOwner(@NotNull Component component) {
    this.component = component;
  }

  public @NotNull Component getComponent() {
    return component;
  }

  @Override
  public String toString() {
    return "ComponentModalTaskOwner(" + component + ')';
  }
}
