// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Represents a toolbar with a visual presentation.
 *
 * @see ActionManager#createActionToolbar(String, ActionGroup, boolean)
 */
public interface ActionToolbar {
  String ACTION_TOOLBAR_PROPERTY_KEY = "ACTION_TOOLBAR";

  /**
   * This is default layout policy for the toolbar. It defines that
   * all toolbar component are in one row / column and they are not wrapped
   * when toolbar is small
   */
  int NOWRAP_LAYOUT_POLICY = 0;
  /**
   * This is experimental layout policy which allow toolbar to
   * wrap components in multiple rows.
   */
  int WRAP_LAYOUT_POLICY = 1;
  /**
   * This is experimental layout policy which allow toolbar auto-hide and show buttons that don't fit into actual side
   */
  int AUTO_LAYOUT_POLICY = 2;

  /**
   * Constraint that's passed to <code>Container.add</code> when ActionButton is added to the toolbar.
   */
  String ACTION_BUTTON_CONSTRAINT = "Constraint.ActionButton";

  /**
   * Constraint that's passed to <code>Container.add</code> when a custom component is added to the toolbar.
   */
  String CUSTOM_COMPONENT_CONSTRAINT = "Constraint.CustomComponent";

  /**
   * Constraint that's passed to <code>Container.add</code> when a Separator is added to the toolbar.
   */
  String SEPARATOR_CONSTRAINT = "Constraint.Separator";

  /**
   * Constraint that's passed to <code>Container.add</code> when a secondary action is added to the toolbar.
   */
  String SECONDARY_ACTION_CONSTRAINT = "Constraint.SecondaryAction";

  @MagicConstant(intValues = {NOWRAP_LAYOUT_POLICY, WRAP_LAYOUT_POLICY, AUTO_LAYOUT_POLICY})
  @interface LayoutPolicy {
  }

  /** This is default minimum size of the toolbar button */
  Dimension DEFAULT_MINIMUM_BUTTON_SIZE = JBUI.size(22, 22);

  Dimension NAVBAR_MINIMUM_BUTTON_SIZE = JBUI.size(20, 20);

  /**
   * @return component which represents the tool bar on UI
   */
  @NotNull
  JComponent getComponent();

  /**
   * @return current layout policy
   * @see #NOWRAP_LAYOUT_POLICY
   * @see #WRAP_LAYOUT_POLICY
   */
  @LayoutPolicy
  int getLayoutPolicy();

  /**
   * Sets new component layout policy. Method accepts {@link #WRAP_LAYOUT_POLICY} and
   * {@link #NOWRAP_LAYOUT_POLICY} values.
   */
  void setLayoutPolicy(@LayoutPolicy int layoutPolicy);

  /**
   * If the value is {@code true} then the all button on toolbar are
   * the same size. It very useful when you create "Outlook" like toolbar.
   * Currently this method can be considered as hot fix.
   */
  void adjustTheSameSize(boolean value);

  /**
   * Sets minimum size of toolbar button. By default all buttons
   * at toolbar has 25x25 pixels size.
   *
   * @throws IllegalArgumentException
   *          if {@code size}
   *          is {@code null}
   */
  void setMinimumButtonSize(@NotNull Dimension size);

  /**
   * Sets toolbar orientation
   *
   * @see SwingConstants#HORIZONTAL
   * @see SwingConstants#VERTICAL
   */
  void setOrientation(@MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL}) int orientation);

  /**
   * @return maximum button height
   */
  int getMaxButtonHeight();

  /**
   * Forces update of the all actions in the toolbars. Actions, however, normally updated automatically every 500 ms.
   */
  void updateActionsImmediately();

  boolean hasVisibleActions();

  /**
   * Will be used for data-context retrieval.
   */
  void setTargetComponent(JComponent component);

  void setReservePlaceAutoPopupIcon(boolean reserve);

  void setSecondaryActionsTooltip(@NotNull String secondaryActionsTooltip);

  void setSecondaryActionsIcon(Icon icon);

  void setSecondaryActionsIcon(Icon icon, boolean hideDropdownIcon);

  @NotNull
  List<AnAction> getActions();

  void setMiniMode(boolean minimalMode);

  @NotNull
  DataContext getToolbarDataContext();

  /**
   * Enables showing titles of separators as labels in the toolbar (off by default).
   */
  default void setShowSeparatorTitles(boolean showSeparatorTitles) {
  }
}
