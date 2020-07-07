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
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class TagButton extends JBLayeredPane implements Disposable {
  protected final JButton myButton;
  private final InplaceButton myCloseButton;
  private final AWTEventListener myEventListener;

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
    myCloseButton = new InplaceButton(new IconButton(OptionsBundle.message("tag.button.tooltip"), AllIcons.Actions.DeleteTag, AllIcons.Actions.DeleteTagHover),
                                      a -> remove(action));
    myCloseButton.setVisible(false);
    myCloseButton.setOpaque(false);
    add(myCloseButton, JLayeredPane.POPUP_LAYER);

    layoutButtons();

    myEventListener = new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        MouseEvent me = (MouseEvent)event;
        Component component = me.getComponent();
        if (component == myButton || component == myCloseButton || component == TagButton.this) {
          if ((MouseEvent.MOUSE_ENTERED == me.getID() || MouseEvent.MOUSE_MOVED == me.getID())) {
            myCloseButton.setVisible(true);
          }
        }
        else if (MouseEvent.MOUSE_MOVED == me.getID()) {
          myCloseButton.setVisible(false);
        }
      }
    };
    Toolkit.getDefaultToolkit().addAWTEventListener(myEventListener, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
  }

  protected void layoutButtons() {
    Dimension size = myButton.getPreferredSize();
    Insets insets = myButton.getBorder().getBorderInsets(myButton);
    setPreferredSize(new Dimension(size.width + 8 - insets.right, size.height + 8 - insets.top));
    myButton.setBounds(0, 8 - insets.top, size.width, size.height);
    myCloseButton.setBounds(size.width - 8 - insets.right, 0, 16, 16);
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
    Toolkit.getDefaultToolkit().removeAWTEventListener(myEventListener);
  }
}
