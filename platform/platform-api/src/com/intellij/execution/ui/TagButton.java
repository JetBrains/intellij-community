// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class TagButton extends JBLayeredPane implements Disposable {
  protected final JButton myButton;
  private final InplaceButton myCloseButton;

  public TagButton(String text, Runnable action) {
    myButton = new JButton(text) {
      @Override
      protected void paintComponent(Graphics g) {
        putClientProperty("JButton.borderColor", hasFocus() ? null : getBackgroundColor());
        super.paintComponent(g);
      }
    };
    myButton.putClientProperty("JButton.backgroundColor", getBackgroundColor());
    myButton.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_BACK_SPACE == e.getKeyCode() || KeyEvent.VK_DELETE == e.getKeyCode()) {
          remove(action);
        }
      }
    });
    add(myButton, JLayeredPane.DEFAULT_LAYER);
    myCloseButton = new InplaceButton(new IconButton(OptionsBundle.message("tag.button.tooltip"), AllIcons.Actions.Close, AllIcons.Actions.CloseDarkGrey),
                                      a -> remove(action));
    myCloseButton.setOpaque(false);
    add(myCloseButton, JLayeredPane.POPUP_LAYER);

    layoutButtons();
  }

  protected void layoutButtons() {
    myButton.setMargin(JBUI.emptyInsets());
    Dimension size = myButton.getPreferredSize();
    int iconWidth = myCloseButton.getIcon().getIconWidth();
    int iconHeight = myCloseButton.getIcon().getIconHeight();
    Dimension tagSize = new Dimension(size.width + iconWidth - myButton.getInsets().right, size.height);
    setPreferredSize(tagSize);
    myButton.setBounds(new Rectangle(tagSize));
    myButton.setMargin(JBUI.insetsRight(iconWidth));
    myCloseButton.setBounds(tagSize.width - iconWidth - JBUI.scale(10), (tagSize.height - iconHeight) / 2 + JBUI.scale(1), iconWidth, iconHeight);
  }

  protected void updateButton(String text, Icon icon) {
    myButton.setText(text);
    myButton.setIcon(icon);
    layoutButtons();
  }

  private void remove(Runnable action) {
    setVisible(false);
    action.run();
  }

  private static Color getBackgroundColor() {
    return JBUI.CurrentTheme.ActionButton.hoverBackground();
  }

  @Override
  public void dispose() {
  }
}
