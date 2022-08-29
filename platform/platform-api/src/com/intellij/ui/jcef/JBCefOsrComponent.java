// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.CEIL;
import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

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
  private volatile @NotNull CefBrowser myBrowser;
  private final @NotNull MyScale myScale = new MyScale();

  private @NotNull Alarm myAlarm;
  private @NotNull Disposable myDisposable;

  JBCefOsrComponent() {
    setPreferredSize(JBCefBrowser.DEF_PREF_SIZE);
    setBackground(JBColor.background());
    addPropertyChangeListener("graphicsConfiguration",
                              e -> myRenderHandler.updateScale(myScale.update(myRenderHandler.getDeviceScaleFactor(myBrowser))));

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
        myBrowser.setFocus(true);
      }
      @Override
      public void focusLost(FocusEvent e) {
        myBrowser.setFocus(false);
      }
    });
  }

  public void setBrowser(@NotNull CefBrowser browser) {
    myBrowser = browser;
  }

  public void setRenderHandler(@NotNull JBCefOsrHandler renderHandler) {
    myRenderHandler = renderHandler;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myDisposable = Disposer.newDisposable();
    myAlarm = AlarmFactory.getInstance().create(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    if (!JBCefBrowserBase.isCefBrowserCreated(myBrowser)) {
      myBrowser.createImmediately();
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    Disposer.dispose(myDisposable);
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

    double scale = myScale.getInverted();
    myAlarm.addRequest(() -> myBrowser.wasResized(CEIL.round(w * scale), CEIL.round(h * scale)), 100);
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);

    double scale = myScale.getIdeBiased();
    myBrowser.sendMouseEvent(new MouseEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      ROUND.round(e.getX() / scale),
      ROUND.round(e.getY() / scale),
      ROUND.round(e.getXOnScreen() / scale),
      ROUND.round(e.getYOnScreen() / scale),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getButton()));

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
    double scale = myScale.getIdeBiased();
    myBrowser.sendMouseWheelEvent(new MouseWheelEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      ROUND.round(e.getX() / scale),
      ROUND.round(e.getY() / scale),
      ROUND.round(e.getXOnScreen() / scale),
      ROUND.round(e.getYOnScreen() / scale),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getScrollType(),
      e.getScrollAmount(),
      (int)val,
      val));
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);

    double scale = myScale.getIdeBiased();
    myBrowser.sendMouseEvent(new MouseEvent(
      e.getComponent(),
      e.getID(),
      e.getWhen(),
      e.getModifiersEx(),
      ROUND.round(e.getX() / scale),
      ROUND.round(e.getY() / scale),
      ROUND.round(e.getXOnScreen() / scale),
      ROUND.round(e.getYOnScreen() / scale),
      e.getClickCount(),
      e.isPopupTrigger(),
      e.getButton()));
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    super.processKeyEvent(e);
    myBrowser.sendKeyEvent(e);
  }

  static class MyScale {
    private volatile double myScale = 1;
    private volatile double myInvertedScale = 1;

    public MyScale update(double scale) {
      myScale = scale;
      if (!JreHiDpiUtil.isJreHiDPIEnabled()) myInvertedScale = 1 / myScale;
      return this;
    }

    public MyScale update(MyScale scale) {
      myScale = scale.myScale;
      myInvertedScale = scale.myInvertedScale;
      return this;
    }

    public double get() {
      return myScale;
    }

    public double getInverted() {
      return JreHiDpiUtil.isJreHiDPIEnabled() ? myScale : myInvertedScale;
    }

    public double getIdeBiased() {
      // IDE-managed HiDPI
      return JreHiDpiUtil.isJreHiDPIEnabled() ? 1 : myScale;
    }

    public double getJreBiased() {
      // JRE-managed HiDPI
      return JreHiDpiUtil.isJreHiDPIEnabled() ? myScale : 1;
    }
  }
}
