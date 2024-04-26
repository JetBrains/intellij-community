// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @deprecated Will be removed with deprecated version of Kotlin UI DSL
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public interface VisualPaddingsProvider {
  @Nullable
  Insets
  getVisualPaddings(@NotNull Component component);
}
