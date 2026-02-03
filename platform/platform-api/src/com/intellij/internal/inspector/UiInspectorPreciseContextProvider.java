// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Allows passing additional information about a component to the UI Inspector.
 *
 * @see UiInspectorContextProvider
 */
public interface UiInspectorPreciseContextProvider {
  @Nullable
  UiInspectorInfo getUiInspectorContext(@NotNull MouseEvent event);

  record UiInspectorInfo(@Nullable String name, @NotNull List<PropertyBean> values, @Nullable Component component) {
  }
}
