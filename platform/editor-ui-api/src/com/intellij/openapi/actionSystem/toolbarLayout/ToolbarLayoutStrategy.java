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

  List<Rectangle> calculateBounds(@NotNull ActionToolbar toolbar);

  Dimension calcPreferredSize(@NotNull ActionToolbar toolbar);

  Dimension calcMinimumSize(@NotNull ActionToolbar toolbar);
}
