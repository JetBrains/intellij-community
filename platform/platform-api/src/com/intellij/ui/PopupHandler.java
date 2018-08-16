/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;
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

/**
 * @author Eugene Belyaev
 */
public abstract class PopupHandler extends MouseAdapter {

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

  public static void installPopupHandler(JComponent component, @NonNls String groupId, String place) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup group = (ActionGroup)actionManager.getAction(groupId);
    installPopupHandler(component, group, place, actionManager);
  }

  @NotNull
  public static MouseListener installPopupHandler(JComponent component,
                                                  @NotNull ActionGroup group,
                                                  String place,
                                                  ActionManager actionManager) {
    return installPopupHandler(component, group, place, actionManager, null);
  }

  @NotNull
  public static MouseListener installPopupHandler(@NotNull JComponent component,
                                                  @NotNull ActionGroup group,
                                                  String place,
                                                  @NotNull ActionManager actionManager,
                                                  @Nullable PopupMenuListener menuListener) {
    if (ApplicationManager.getApplication() == null) return new MouseAdapter(){};
    PopupHandler popupHandler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(place, group);
        popupMenu.setTargetComponent(component);
        JPopupMenu menu = popupMenu.getComponent();
        if (menuListener != null) menu.addPopupMenuListener(menuListener);
        menu.show(comp, x, y);
      }
    };
    component.addMouseListener(popupHandler);
    return popupHandler;
  }

  @NotNull
  public static MouseListener installFollowingSelectionTreePopup(@NotNull JTree tree,
                                                                 @NotNull ActionGroup group,
                                                                 String place,
                                                                 @NotNull ActionManager actionManager) {
    return installConditionalPopup(tree, group, place, actionManager, (comp, x, y) ->
      tree.getPathForLocation(x, y) != null &&
      Arrays.binarySearch(Objects.requireNonNull(tree.getSelectionRows()), tree.getRowForLocation(x, y)) > -1);
  }

  @NotNull
  public static MouseListener installRowSelectionTablePopup(@NotNull JTable table,
                                                            @NotNull ActionGroup group,
                                                            String place,
                                                            @NotNull ActionManager actionManager) {
    return installConditionalPopup(table, group, place, actionManager, (comp, x, y) ->
      Arrays.binarySearch(table.getSelectedRows(), table.rowAtPoint(new Point(x, y))) > -1);
  }

  @NotNull
  private static MouseListener installConditionalPopup(@NotNull JComponent component,
                                                      @NotNull ActionGroup group,
                                                      String place,
                                                      @NotNull ActionManager actionManager,
                                                      @NotNull ShowPopupPredicate condition) {
    if (ApplicationManager.getApplication() == null) return new MouseAdapter(){};
    PopupHandler handler = new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        if (condition.shouldShowPopup(comp, x, y)) {
          ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(place, group);
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    };
    component.addMouseListener(handler);
    return handler;
  }

  public static MouseListener installUnknownPopupHandler(JComponent component, ActionGroup group, ActionManager actionManager) {
    return installPopupHandler(component, group, ActionPlaces.UNKNOWN, actionManager);
  }

  @FunctionalInterface
  private interface ShowPopupPredicate {
    boolean shouldShowPopup(Component comp, int x, int y);
  }
}