// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

public class TagButton extends JButton implements Disposable {
  private final AWTEventListener myEventListener;
  private JBPopup myPopup;
  private InplaceButton myCloseButton;
  private boolean myCanClose;

  public TagButton(String text, Runnable action) {
    super(text);
    setOpaque(false);
    putClientProperty("JButton.backgroundColor", getBackgroundColor());
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_BACK_SPACE == e.getKeyCode() || KeyEvent.VK_DELETE == e.getKeyCode()) {
          remove(action);
        }
      }
    });
    myEventListener = new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        MouseEvent me = (MouseEvent)event;
        Component component = me.getComponent();
        if (component != TagButton.this && component != myCloseButton) {
          if (MouseEvent.MOUSE_MOVED == me.getID()) {
            hidePopup();
          }
        }
        else if ((MouseEvent.MOUSE_ENTERED == me.getID() || MouseEvent.MOUSE_MOVED == me.getID()) &&
                (myPopup == null || !myPopup.isVisible())) {
          myCloseButton = new InplaceButton(new IconButton(OptionsBundle.message("tag.button.tooltip"), AllIcons.Actions.DeleteTag, AllIcons.Actions.DeleteTagHover),
                              a -> remove(action));
          myCloseButton.setOpaque(false);
          myCloseButton.setPreferredSize(new Dimension(16, 16));
          myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(myCloseButton, null).
            setResizable(false).setMovable(false).setFocusable(false).setShowBorder(false).setCancelCallback(() -> myCanClose).createPopup();
          myCanClose = false;
          myPopup.show(new RelativePoint(TagButton.this, new Point(getWidth() - 12, -4)));
        }
      }
    };
    Toolkit.getDefaultToolkit().addAWTEventListener(myEventListener, AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
  }

  private void remove(Runnable action) {
    hidePopup();
    setVisible(false);
    action.run();
  }

  private static Color getBackgroundColor() {
    return JBUI.CurrentTheme.ActionButton.hoverBackground();
  }

  private void hidePopup() {
    if (myPopup != null && myPopup.isVisible()) {
      myCanClose = true;
      myPopup.cancel();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    putClientProperty("JButton.borderColor", hasFocus() ? null : getBackgroundColor());
    super.paintComponent(g);
  }

  @Override
  public void dispose() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(myEventListener);
  }
}
