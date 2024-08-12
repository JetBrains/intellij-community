// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ListExpandableItemsHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.*;

/**
 * @author Konstantin Bulenkov
 */
public final class CompletionExtender extends ListExpandableItemsHandler {

  public CompletionExtender(final @NotNull JList list) {
    super(list);
    list.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        JScrollPane scrollPane = ComponentUtil.getScrollPane(myComponent);
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
}
