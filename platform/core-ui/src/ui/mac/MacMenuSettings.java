// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class MacMenuSettings {
  // see also `PathManager#loadProperties`
  public static final boolean isJbSystemMenu =
    OS.CURRENT == OS.macOS && Boolean.parseBoolean(System.getProperty("jbScreenMenuBar.enabled", "true"));

  public static final boolean isSystemMenu =
    OS.CURRENT == OS.macOS && (isJbSystemMenu || Boolean.getBoolean("apple.laf.useScreenMenuBar"));
}
