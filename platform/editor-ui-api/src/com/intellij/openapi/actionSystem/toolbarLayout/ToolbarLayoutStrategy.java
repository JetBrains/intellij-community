// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout;

import com.intellij.openapi.actionSystem.ActionToolbar;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

public interface ToolbarLayoutStrategy {

  ToolbarLayoutStrategy NOWRAP_STRATEGY = new RightActionsAdjusterStrategyWrapper(new NoWrapLayoutStrategy(false));
  ToolbarLayoutStrategy WRAP_STRATEGY = new RightActionsAdjusterStrategyWrapper(new WrapLayoutStrategy(false));
  ToolbarLayoutStrategy AUTOLAYOUT_STRATEGY = new RightActionsAdjusterStrategyWrapper(new AutoLayoutStrategy(false, false));
  /**
   * This strategy dynamically adjusts component sizes, ranging from their preferred size to their minimum size.
   * Should the parent component lack sufficient space, a compress operation is triggered on its child components.
   * Preferentially, the largest components are compressed first to optimize the use of available space.
   * Note: for correct work, it's necessary to have a parent component for row with toolbar.
   */
  ToolbarLayoutStrategy COMPRESSING_STRATEGY = CompressingLayoutStrategy.INSTANCE;

  List<Rectangle> calculateBounds(@NotNull ActionToolbar toolbar);

  Dimension calcPreferredSize(@NotNull ActionToolbar toolbar);

  Dimension calcMinimumSize(@NotNull ActionToolbar toolbar);
}
