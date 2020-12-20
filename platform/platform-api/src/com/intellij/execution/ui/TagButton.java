// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsContexts;
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

  public TagButton(@NlsContexts.Button String text, Runnable action) {
    myButton = new JButton(text) {
      @Override
      protected void paintComponent(Graphics g) {
        putClientProperty("JButton.borderColor", hasFocus() ? null : getBackgroundColor());
        super.paintComponent(g);
      }
    };
    myButton.putClientProperty("styleTag", true);
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
    Dimension iconSize = myCloseButton.getPreferredSize();
    int inset = JBUI.scale(3);
    Dimension tagSize = new Dimension(size.width + iconSize.width - inset * 2, size.height);
    setPreferredSize(tagSize);
    myButton.setBounds(new Rectangle(tagSize));
    myButton.setMargin(JBUI.insetsRight(iconSize.width));
    Point p = new Point(tagSize.width - iconSize.width - inset * 3,
                        (tagSize.height - iconSize.height) / 2 + JBUI.scale(1));
    myCloseButton.setBounds(new Rectangle(p, iconSize));
  }

  protected void updateButton(@NlsContexts.Button String text, Icon icon, boolean isEnabled) {
    myButton.setText(text);
    myButton.setIcon(icon);
    myButton.setEnabled(isEnabled);
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
