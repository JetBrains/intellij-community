// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Prefer {@link #installPopupMenu(JComponent, ActionGroup, String)} or {@link #installPopupMenu(JComponent, String, String)}
 * to direct implementation if no special handing is required.
 */
public abstract class PopupHandler extends MouseAdapter {

  private static final Logger LOG = Logger.getInstance(PopupHandler.class);

  private static final PopupHandler EMPTY_HANDLER = new PopupHandler() {
    @Override
    public void invokePopup(Component comp, int x, int y) {
    }
  };

  public abstract void invokePopup(Component comp, int x, int y);

  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  /** @deprecated use {@link #installPopupMenu(JComponent, String, String)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static void installPopupHandler(@NotNull JComponent component,
                                         @NotNull String groupId,
                                         @NotNull String place) {
    installPopupMenu(component, groupId, place);
  }

  public static @NotNull PopupHandler installPopupMenu(@NotNull JComponent component,
                                                       @NotNull String groupId,
                                                       @NotNull String place) {
    return installPopupMenu(component, am -> {
      AnAction action = am.getAction(groupId);
      if (action instanceof ActionGroup) {
        return (ActionGroup)action;
      }
      LOG.warn("'" + groupId + "' invoked at '" + place + "' is " + (action == null ? "null" : "not an action group"));
      return null;
    }, place, null, null);
  }

  /** @deprecated use {@link #installPopupMenu(JComponent, ActionGroup, String)} instead */
  @Deprecated
  public static @NotNull MouseListener installPopupHandler(@NotNull JComponent component,
                                                           @NotNull ActionGroup group,
                                                           @NotNull String place) {
    return installPopupMenu(component, __ -> group, place, null, null);
  }

  public static @NotNull PopupHandler installPopupMenu(@NotNull JComponent component,
                                                       @NotNull ActionGroup group,
                                                       @NotNull String place) {
    return installPopupMenu(component, __ -> group, place, null, null);
  }

  public static @NotNull PopupHandler installPopupMenu(@NotNull JComponent component,
                                                       @NotNull ActionGroup group,
                                                       @NotNull String place,
                                                       @Nullable PopupMenuListener menuListener) {
    return installPopupMenu(component, __ -> group, place, null, menuListener);
  }

  /** @deprecated use {@link #installPopupMenu(JComponent, ActionGroup, String)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static @NotNull MouseListener installPopupHandler(@NotNull JComponent component,
                                                           @NotNull ActionGroup group,
                                                           @NotNull String place,
                                                           @Nullable ActionManager actionManager) {
    return installPopupMenu(component, __ -> group, place, actionManager, null);
  }

  /** @deprecated use {@link #installPopupMenu(JComponent, ActionGroup, String)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval (inVersion = "2021.2")
  public static @NotNull MouseListener installPopupHandler(@NotNull JComponent component,
                                                           @NotNull ActionGroup group,
                                                           @NotNull String place,
                                                           @Nullable ActionManager actionManager,
                                                           @Nullable PopupMenuListener menuListener) {
    return installPopupMenu(component, __ -> group, place, actionManager, menuListener);
  }

  private static @NotNull PopupHandler installPopupMenu(@NotNull JComponent component,
                                                        @NotNull Function<ActionManager, ActionGroup> group,
                                                        @NotNull String place,
                                                        @Nullable ActionManager actionManager,
                                                        @Nullable PopupMenuListener menuListener) {
    if (ApplicationManager.getApplication() == null) {
      return EMPTY_HANDLER;
    }

    PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionManager manager = actionManager == null ? ActionManager.getInstance() : actionManager;
        ActionGroup actionGroup = group.apply(manager);
        if (actionGroup == null) return;
        ActionPopupMenu popupMenu = manager.createActionPopupMenu(place, actionGroup);
        popupMenu.setTargetComponent(component);
        JPopupMenu menu = popupMenu.getComponent();
        if (menuListener != null) {
          menu.addPopupMenuListener(menuListener);
        }
        menu.show(comp, x, y);
      }
    };
    component.addMouseListener(popupHandler);
    return popupHandler;
  }

  public static @NotNull PopupHandler installFollowingSelectionTreePopup(@NotNull JTree tree,
                                                                         @NotNull ActionGroup group,
                                                                         @NotNull String place) {
    return (PopupHandler)installFollowingSelectionTreePopup(tree, group, place, null);
  }

  /** @deprecated use {@link #installFollowingSelectionTreePopup(JTree, ActionGroup, String)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static @NotNull MouseListener installFollowingSelectionTreePopup(@NotNull JTree tree,
                                                                          @NotNull ActionGroup group,
                                                                          @NotNull String place,
                                                                          @Nullable ActionManager actionManager) {
    return installConditionalPopup(tree, group, place, actionManager, (comp, x, y) -> {
      return tree.getPathForLocation(x, y) != null &&
             Arrays.binarySearch(Objects.requireNonNull(tree.getSelectionRows()), tree.getRowForLocation(x, y)) > -1;
    });
  }

  public static @NotNull PopupHandler installRowSelectionTablePopup(@NotNull JTable table,
                                                                    @NotNull ActionGroup group,
                                                                    @NotNull String place) {
    return installConditionalPopup(table, group, place, null, (comp, x, y) ->
      Arrays.binarySearch(table.getSelectedRows(), table.rowAtPoint(new Point(x, y))) > -1);
  }

  public static @NotNull PopupHandler installSelectionListPopup(@NotNull JList<?> list,
                                                                @NotNull ActionGroup group,
                                                                @NotNull String place) {
    return installConditionalPopup(list, group, place, null, (comp, x, y) -> ListUtil.isPointOnSelection(list, x, y));
  }

  private static @NotNull PopupHandler installConditionalPopup(@NotNull JComponent component,
                                                                @NotNull ActionGroup group,
                                                                @NotNull String place,
                                                                @Nullable ActionManager actionManager,
                                                                @NotNull ShowPopupPredicate condition) {
    if (ApplicationManager.getApplication() == null) {
      return EMPTY_HANDLER;
    }

    PopupHandler handler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        if (condition.shouldShowPopup(comp, x, y)) {
          ActionManager manager = actionManager == null ? ActionManager.getInstance() : actionManager;
          ActionPopupMenu popupMenu = manager.createActionPopupMenu(place, group);
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    };
    component.addMouseListener(handler);
    return handler;
  }

  /** @deprecated Use {@link #installPopupMenu(JComponent, ActionGroup, String)} */
  @Deprecated
  public static MouseListener installUnknownPopupHandler(JComponent component, ActionGroup group, ActionManager actionManager) {
    return installPopupMenu(component, __ -> group, ActionPlaces.UNKNOWN, actionManager, null);
  }

  /** @deprecated Use {@link #installPopupMenu(JComponent, ActionGroup, String)} */
  @Deprecated
  public static MouseListener installUnknownPopupHandler(JComponent component, ActionGroup group) {
    return installPopupMenu(component, __ -> group, ActionPlaces.UNKNOWN, null, null);
  }

  @FunctionalInterface
  private interface ShowPopupPredicate {
    boolean shouldShowPopup(Component comp, int x, int y);
  }
}