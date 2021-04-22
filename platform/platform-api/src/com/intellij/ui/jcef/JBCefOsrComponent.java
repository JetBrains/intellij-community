// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * A lightweight component on which an off-screen browser is rendered.
 *
 * @see JBCefBrowser#getComponent()
 * @see JBCefOsrHandler
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

    enableEvents(AWTEvent.KEY_EVENT_MASK |
                 AWTEvent.MOUSE_EVENT_MASK |
                 AWTEvent.MOUSE_WHEEL_EVENT_MASK |
                 AWTEvent.MOUSE_MOTION_EVENT_MASK);

    setFocusable(true);
    setRequestFocusEnabled(true);
    // [tav] todo: so far the browser component can not be traversed out
    setFocusTraversalKeysEnabled(false);
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myBrowser.getCefBrowser().setFocus(true);
      }
      @Override
      public void focusLost(FocusEvent e) {
        myBrowser.getCefBrowser().setFocus(false);
      }
    });
  }

  public void setBrowser(@NotNull JBCefBrowser browser) {
    myBrowser = browser;
    myAlarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, browser);
  }

  public void setRenderHandler(@NotNull JBCefOsrHandler renderHandler) {
    myRenderHandler = renderHandler;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (!myBrowser.isCefBrowserCreated()) {
      myBrowser.getCefBrowser().createImmediately();
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    myRenderHandler.paint((Graphics2D)g);
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
    myBrowser.getCefBrowser().sendMouseEvent(e);
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      requestFocusInWindow();
    }
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    super.processMouseWheelEvent(e);

    double val = e.getPreciseWheelRotation() *
                 RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor");
    if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
      val *= -1;
    }
    myBrowser.getCefBrowser().sendMouseWheelEvent(new MouseWheelEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      e.getX(),
      e.getY(),
      e.getXOnScreen(),
      e.getYOnScreen(),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getScrollType(),
      e.getScrollAmount(),
      (int)val,
      val));
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
