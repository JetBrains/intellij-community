// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.Future;

/**
 * A toolbar containing action buttons.
 * <ul>
 * <li>Toolbars can be laid out horizontally or vertically.
 * <li>Toolbars can have a border or be embedded seamlessly in the header of a tool window.
 * </ul>
 * If the toolbar belongs to a specific component (such as a tool window panel), set it via {@link #setTargetComponent(JComponent)}.
 *
 * @see ActionManager#createActionToolbar(String, ActionGroup, boolean)
 */
public interface ActionToolbar {
  String ACTION_TOOLBAR_PROPERTY_KEY = "ACTION_TOOLBAR";

  /**
   * This is the default layout policy for the toolbar.
   * It defines that all toolbar components are in a single row/column, and they are not wrapped when the toolbar is small.
   */
  int NOWRAP_LAYOUT_POLICY = 0;
  /**
   * This is an experimental layout policy that allows the toolbar to wrap components in multiple rows.
   */
  int WRAP_LAYOUT_POLICY = 1;
  /**
   * This is an experimental layout policy that allows the toolbar to auto-hide and show buttons that don't fit into actual side.
   */
  int AUTO_LAYOUT_POLICY = 2;

  /**
   * Constraint that's passed to <code>Container.add</code> when an ActionButton is added to the toolbar.
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

  String SECONDARY_ACTION_PROPERTY = "ClientProperty.SecondaryAction";

  @MagicConstant(intValues = {NOWRAP_LAYOUT_POLICY, WRAP_LAYOUT_POLICY, AUTO_LAYOUT_POLICY})
  @interface LayoutPolicy {
  }

  /** This is the default minimum size of the toolbar button. */
  @NotNull
  Dimension DEFAULT_MINIMUM_BUTTON_SIZE = JBUI.size(22, 22);

  @NotNull
  Dimension NAVBAR_MINIMUM_BUTTON_SIZE = JBUI.size(20, 20);

  static Dimension experimentalToolbarMinimumButtonSize() {
    return JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonSize();
  }

  /**
   * @return the component that represents the toolbar on the UI
   */
  @NotNull JComponent getComponent();

  /**
   * Returns the "action place" of this toolbar.
   * @see ActionManager#createActionToolbar
   * */
  @NotNull String getPlace();

  /**
   * Sets the layout policy for the toolbar.
   *
   * @param layoutPolicy the layout policy to set. Use one of the following constants:
   *                     {@link ActionToolbar#NOWRAP_LAYOUT_POLICY},
   *                     {@link ActionToolbar#WRAP_LAYOUT_POLICY},
   *                     {@link ActionToolbar#AUTO_LAYOUT_POLICY}.
   * @deprecated This method is deprecated since version 2024.1 and will be removed in a future release.
   * Method does not have any effect any more. Use {@link ActionToolbar#setLayoutStrategy(ToolbarLayoutStrategy)} instead.
   */
  @Deprecated(since = "2024.1", forRemoval = true)
  default void setLayoutPolicy(@LayoutPolicy int layoutPolicy) {}

  @NotNull
  ToolbarLayoutStrategy getLayoutStrategy();

  void setLayoutStrategy(@NotNull ToolbarLayoutStrategy strategy);

  /**
   * If the value is {@code true}, all buttons on the toolbar have the same size.
   * It is useful when you create an "Outlook"-like toolbar.
   *
   * @deprecated This method is deprecated since version 2024.1 and will be removed in a future release.
   * Method does not have any effect any more. Please use {@link ActionToolbar#setLayoutStrategy(ToolbarLayoutStrategy)} instead
   */
  @Deprecated(since = "2024.1", forRemoval = true)
  default void adjustTheSameSize(boolean value) {}

  /**
   * Sets the minimum size of the toolbar buttons.
   */
  void setMinimumButtonSize(@NotNull Dimension size);

  @NotNull Dimension getMinimumButtonSize();

  /**
   * Sets the toolbar orientation.
   *
   * @see SwingConstants#HORIZONTAL
   * @see SwingConstants#VERTICAL
   */
  void setOrientation(@MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL}) int orientation);

  /**
   * Returns the toolbar orientation.
   * */
  @MagicConstant(intValues = {SwingConstants.HORIZONTAL, SwingConstants.VERTICAL})
  int getOrientation();

  /**
   * @return the maximum button height
   */
  int getMaxButtonHeight();

  /**
   * Async toolbars are not updated immediately despite the name of the method.
   * Toolbars are updated automatically on showing and every 500 ms if activity count is changed.
   *
   * @deprecated Use {@link #updateActionsAsync()} instead
   */
  @Deprecated
  void updateActionsImmediately();

  @RequiresEdt
  @NotNull Future<?> updateActionsAsync();

  boolean hasVisibleActions();

  @ApiStatus.Internal
  @Nullable JComponent getTargetComponent();

  /**
   * Will be used for data-context retrieval.
   */
  void setTargetComponent(@Nullable JComponent component);

  void setReservePlaceAutoPopupIcon(boolean reserve);

  boolean isReservePlaceAutoPopupIcon();

  void setSecondaryActionsTooltip(@NotNull @NlsContexts.Tooltip String secondaryActionsTooltip);

  void setSecondaryActionsShortcut(@NotNull String secondaryActionsShortcut);

  void setSecondaryActionsIcon(Icon icon);

  void setSecondaryActionsIcon(Icon icon, boolean hideDropdownIcon);

  void setLayoutSecondaryActions(boolean val);

  @NotNull ActionGroup getActionGroup();

  @NotNull List<AnAction> getActions();

  void setMiniMode(boolean minimalMode);

  @NotNull DataContext getToolbarDataContext();

  /**
   * Enables showing titles of separators as labels in the toolbar (off by default).
   */
  default void setShowSeparatorTitles(boolean showSeparatorTitles) {
  }

  default void addListener(@NotNull ActionToolbarListener listener, @NotNull Disposable parentDisposable) {
  }


  /**
   * @return {@link ActionToolbar} that contains the specified {@code component},
   * or {@code null} if it is not placed on any toolbar
   */
  static @Nullable ActionToolbar findToolbarBy(@Nullable Component component) {
    return ComponentUtil.getStrictParentOfType(ActionToolbar.class, component);
  }

  /**
   * @return {@link DataContext} constructed for the specified {@code component}
   * that can be placed on an action toolbar
   */
  static @NotNull DataContext getDataContextFor(@Nullable Component component) {
    ActionToolbar toolbar = findToolbarBy(component);
    return toolbar != null ? toolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext(component);
  }
}
