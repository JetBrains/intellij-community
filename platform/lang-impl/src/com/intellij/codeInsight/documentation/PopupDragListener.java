// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

@Internal
public final class PopupDragListener extends MouseAdapter {

  private final @NotNull JBPopup myPopup;

  private PopupDragListener(@NotNull JBPopup popup) {
    myPopup = popup;
  }

  private Point myInitialPress;

  @Override
  public void mousePressed(MouseEvent e) {
    myInitialPress = e.getPoint();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (myInitialPress == null) {
      return;
    }
    Point location = myPopup.getLocationOnScreen();
    myPopup.setLocation(new Point(location.x + e.getX() - myInitialPress.x, location.y + e.getY() - myInitialPress.y));
    e.consume();
  }

  /**
   * Install a listener which moves the popup, when mouse is dragged on top of the component.
   *
   * @param component the component which receives mouse events (expected to be inside the popup)
   * @param popup     the popup to drag
   */
  @RequiresEdt
  public static void dragPopupByComponent(@NotNull JBPopup popup, @NotNull JComponent component) {
    var listener = new PopupDragListener(popup);
    component.addMouseListener(listener);
    component.addMouseMotionListener(listener);
    Disposer.register(popup, () -> {
      component.removeMouseMotionListener(listener);
      component.removeMouseListener(listener);
    });
  }
}
