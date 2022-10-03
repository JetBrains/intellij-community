// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.marketplace.PluginReviewComment;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.hover.HoverStateListener;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ReviewCommentListContainer extends JBPanelWithEmptyText {
  private final HoverStateListener myListener;

  public ReviewCommentListContainer() {
    setLayout(new VerticalLayout(0));

    setOpaque(true);
    setBackground(PluginManagerConfigurable.MAIN_BG_COLOR);

    myListener = new HoverStateListener() {
      @Override
      protected void hoverChanged(@NotNull Component component, boolean hovered) {
        if (component instanceof ReviewCommentComponent) {
          ((ReviewCommentComponent)component).setState(hovered ? EventHandler.SelectionType.HOVER : EventHandler.SelectionType.NONE);
        }
      }
    };

    getEmptyText().setText(IdeBundle.message("plugins.review.panel.empty.text"));
  }

  public void addComments(@NotNull List<PluginReviewComment> comments) {
    for (PluginReviewComment comment : comments) {
      ReviewCommentComponent component = new ReviewCommentComponent(comment);
      add(component, VerticalLayout.FILL_HORIZONTAL);
      myListener.addTo(component);
    }
  }

  public void fullRepaint() {
    doLayout();
    revalidate();
    repaint();
  }

  public void clear() {
    removeAll();
  }
}