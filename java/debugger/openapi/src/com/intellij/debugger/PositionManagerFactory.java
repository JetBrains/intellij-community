// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider for a {@link PositionManager},
 * <p>{@link #createPositionManager} is called once on debug process start
 */
public abstract class PositionManagerFactory {
  public static final ExtensionPointName<PositionManagerFactory> EP_NAME = ExtensionPointName.create("com.intellij.debugger.positionManagerFactory");

  @Nullable
  public abstract PositionManager createPositionManager(@NotNull DebugProcess process);
}
