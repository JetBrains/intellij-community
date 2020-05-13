// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ToolWindowFactoryEx extends ToolWindowFactory {
  /**
   * Return custom anchor or null to use anchor defined in Tool Window Registration or customized by user.
   */
  default @Nullable ToolWindowAnchor getAnchor() {
    return null;
  }
}
