// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A lightweight component on which an off-screen browser is rendered.
 *
 * @see JBCefBrowser#create(JBCefBrowser.RenderingType, JBCefClient, String, boolean)
 * @see JBCefBrowser#getComponent()
 * @author tav
 */
@SuppressWarnings("NotNullFieldNotInitialized")
class JBCefOsrComponent extends JPanel {
  private volatile @NotNull JBCefOsrHandler myRenderHandler;
  private volatile @NotNull JBCefBrowser myBrowser;
  private double myScale = 1.0;

  private volatile @NotNull Alarm myAlarm;

  JBCefOsrComponent() {
    setPreferredSize(JBCefBrowser.DEF_PREF_SIZE);
    setBackground(JBColor.background());
    addPropertyChangeListener("graphicsConfiguration", e -> myRenderHandler.updateScale(myScale = JBUIScale.sysScale(this)));

    setFocusable(true);
    setRequestFocusEnabled(true);
    enableEvents(AWTEvent.KEY_EVENT_MASK |
                 AWTEvent.MOUSE_EVENT_MASK |
                 AWTEvent.MOUSE_WHEEL_EVENT_MASK |
                 AWTEvent.MOUSE_MOTION_EVENT_MASK);
  }

  public void setBrowser(@NotNull JBCefBrowser browser) {
    myBrowser = browser;
    myAlarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, browser);
  }

  public void setRenderHandler(@NotNull JBCefOsrHandler renderHandler) {
    myRenderHandler = renderHandler;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myRenderHandler.paint((Graphics2D)g);
  }

  @SuppressWarnings("deprecation")
  @Override
  public void show() {
    super.show();
    myRenderHandler.notifyComponentShown();
  }

  @SuppressWarnings("deprecation")
  @Override
  public void reshape(int x, int y, int w, int h) {
    super.reshape(x, y, w, h);
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> myBrowser.getCefBrowser().wasResized((int)Math.ceil(w * myScale), (int)Math.ceil(h * myScale)), 100);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    boolean mousePressed = e.getID() == MouseEvent.MOUSE_PRESSED;
    if (mousePressed) myRenderHandler.notifyMousePressed();
    myBrowser.getCefBrowser().sendMouseEvent(e);
    if (mousePressed) requestFocusInWindow();
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    super.processMouseWheelEvent(e);
    myBrowser.getCefBrowser().sendMouseWheelEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    myBrowser.getCefBrowser().sendMouseEvent(e);
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
    myBrowser.getCefBrowser().sendKeyEvent(e);
  }
}
