// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class LockedIconLayerProvider implements IconLayerProvider {
  @Override
  public Icon getLayerIcon(@NotNull Iconable element, boolean isLocked) {
    return isLocked && Registry.is("ide.locked.icon.enabled", false) ? PlatformIcons.LOCKED_ICON : null;
  }

  @NotNull
  @Override
  public String getLayerDescription() {
    return "Read-only";
  }
}
