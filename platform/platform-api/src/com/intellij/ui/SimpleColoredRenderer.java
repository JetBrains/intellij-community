/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

public class SimpleColoredRenderer extends SimpleColoredComponent {
  private TableCellState myCellState = new TableCellState();

  public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
    myCellState.collectState(table, isSelected, hasFocus, row, column);
  }

  @Override
  public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, boolean isMainText) {
    super.append(fragment, modifyAttributes(attributes), isMainText);
  }

  @Override
  protected void revalidateAndRepaint() {
    // no need for this in a renderer
  }

  protected SimpleTextAttributes modifyAttributes(final SimpleTextAttributes attributes) {
    return myCellState.modifyAttributes(attributes);
  }

  public TableCellState getCellState() {
    return myCellState;
  }

  public void setCellState(TableCellState cellState) {
    myCellState = cellState;
  }

  protected boolean shouldPaintBackground() {
    return true;
  }

  @Override
  protected void paintComponent(Graphics g) {
    if (shouldPaintBackground()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    super.paintComponent(g);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleContextDelegateWithContextMenu(super.getAccessibleContext()) {
        @Override
        protected void doShowContextMenu() {
          ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true);
        }

        @Override
        protected Container getDelegateParent() {
          return getParent();
        }
      };
    }
    return accessibleContext;
  }
}
