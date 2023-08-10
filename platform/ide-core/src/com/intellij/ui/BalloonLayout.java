// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.openapi.ui.popup.Balloon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BalloonLayout {
  void add(@NotNull Balloon balloon);

  void add(@NotNull Balloon balloon, @Nullable Object layoutData);

  @ApiStatus.Internal
  void closeAll();
}
