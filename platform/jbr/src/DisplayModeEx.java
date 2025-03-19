// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jbr;

import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * WARNING: For internal usage only.
 */
public interface DisplayModeEx {
  boolean isDefault(@NotNull DisplayMode dm);
}

final class DefDisplayModeEx implements DisplayModeEx {
  @Override
  public boolean isDefault(@NotNull DisplayMode dm) {
    return true;
  }
}

final class JBDisplayModeEx implements DisplayModeEx {
  private static final MethodInvocator isDefaultInvocator = new MethodInvocator(DisplayMode.class, "isDefault", null);

  @Override
  public boolean isDefault(@NotNull DisplayMode dm) {
    return isDefaultInvocator.isAvailable() ? (Boolean)isDefaultInvocator.invoke(dm) : true;
  }
}
