// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.toolbarLayout;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public interface ToolbarLayoutStrategy {

  ToolbarLayoutStrategy HORIZONTAL_NOWRAP_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new NoWrapLayoutStrategy(SwingConstants.HORIZONTAL, false));

  ToolbarLayoutStrategy VERTICAL_NOWRAP_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new NoWrapLayoutStrategy(SwingConstants.VERTICAL, false));

  ToolbarLayoutStrategy HORIZONTAL_WRAP_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new WrapLayoutStrategy(SwingConstants.HORIZONTAL, false));

  ToolbarLayoutStrategy VERTICAL_WRAP_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new WrapLayoutStrategy(SwingConstants.VERTICAL, false));

  ToolbarLayoutStrategy HORIZONTAL_AUTOLAYOUT_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new AutoLayoutStrategy(SwingConstants.HORIZONTAL, false));

  ToolbarLayoutStrategy VERTICAL_AUTOLAYOUT_STRATEGY =
    new RightActionsAdjusterStrategyWrapper(new AutoLayoutStrategy(SwingConstants.VERTICAL, false));

  List<Rectangle> calculateBounds(@NotNull ActionToolbar toolbar);

  Dimension calcPreferredSize(@NotNull ActionToolbar toolbar);

  Dimension calcMinimumSize(@NotNull ActionToolbar toolbar);

  default void setMinimumButtonSize(@NotNull JBDimension size) {}
}
