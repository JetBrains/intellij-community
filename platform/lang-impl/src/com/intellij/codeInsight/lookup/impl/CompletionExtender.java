/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.ui.ListExpandableItemsHandler;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.Rectangle;
import java.awt.event.*;

/**
 * @author Konstantin Bulenkov
 */
public class CompletionExtender extends ListExpandableItemsHandler {

  public CompletionExtender(@NotNull final JList list) {
    super(list);
    list.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        JScrollPane scrollPane = JBScrollPane.findScrollPane(myComponent);
        if (scrollPane != null) {
          final JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
          final JScrollBar horizontalScrollBar = scrollPane.getVerticalScrollBar();
          final AdjustmentListener listener = new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
              updateCurrentSelection();
            }
          };
          if (verticalScrollBar != null) verticalScrollBar.addAdjustmentListener(listener);
          if (horizontalScrollBar != null) horizontalScrollBar.addAdjustmentListener(listener);

          list.removeComponentListener(this);
        }
      }
    });
  }
  @Override
  protected void handleSelectionChange(Integer selected, boolean processIfUnfocused) {
    super.handleSelectionChange(myComponent.getSelectedIndex(), true);
  }

  @Override
  protected void onFocusLost() {
    //don't hide hint
  }

  @Override
  protected void handleMouseEvent(MouseEvent e, boolean forceUpdate) {
    // don't show or hide hint on mouse events
  }

  @Override
  protected boolean isPaintBorder() {
    return false;
  }

  @Override
  protected boolean isPopup() {
    return false;
  }

  @Override
  protected Rectangle getVisibleRect(Integer key) {
    try {
      Rectangle bounds = myComponent.getCellBounds(key, key);
      if (bounds != null) return bounds;
    }
    catch (IndexOutOfBoundsException ignored) {
    }
    return super.getVisibleRect(key);
  }
}
