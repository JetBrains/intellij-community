// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public interface StatusBarListener extends EventListener {
  default void widgetAdded(@NotNull StatusBarWidget widget, @NonNls @Nullable String anchor) {
  }

  default void widgetUpdated(@NonNls @NotNull String id) {
  }

  default void widgetRemoved(@NonNls @NotNull String id) {
  }
}
