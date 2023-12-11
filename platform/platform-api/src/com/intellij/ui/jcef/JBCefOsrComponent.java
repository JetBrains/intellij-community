// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scroll.TouchScrollUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import org.cef.browser.CefBrowser;
import org.cef.input.CefCompositionUnderline;
import org.cef.input.CefTouchEvent;
import org.cef.misc.CefRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextHitInfo;
import java.awt.geom.Point2D;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
  static final int RESIZE_DELAY_MS = Integer.getInteger("ide.browser.jcef.resize_delay_ms", 100);
  private volatile @NotNull JBCefOsrHandler myRenderHandler;
  private final @NotNull InputMethodAdapter myInputMethodAdapter = new InputMethodAdapter();
  private volatile @NotNull CefBrowser myBrowser;
  private final @NotNull MyScale myScale = new MyScale();

  private final @NotNull AtomicLong myScheduleResizeMs = new AtomicLong(-1);
  private @Nullable Alarm myAlarm;
  private @NotNull Disposable myDisposable;
  private @NotNull MouseWheelEventsAccumulator myWheelEventsAccumulator;

  JBCefOsrComponent(boolean isMouseWheelEventEnabled) {
    setPreferredSize(JBCefBrowser.DEF_PREF_SIZE);
    setBackground(JBColor.background());
    addPropertyChangeListener("graphicsConfiguration",
                              e -> {
                                myRenderHandler.updateScale(myScale.update(myRenderHandler.getDeviceScaleFactor(myBrowser)));
                                myBrowser.notifyScreenInfoChanged();
                              });

    enableEvents(AWTEvent.KEY_EVENT_MASK |
                 AWTEvent.MOUSE_EVENT_MASK |
                 (isMouseWheelEventEnabled ? AWTEvent.MOUSE_WHEEL_EVENT_MASK : 0L) |
                 AWTEvent.MOUSE_MOTION_EVENT_MASK |
                 AWTEvent.INPUT_METHOD_EVENT_MASK);
    enableInputMethods(true);

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

    addInputMethodListener(myInputMethodAdapter);
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
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myDisposable);
    myWheelEventsAccumulator = new MouseWheelEventsAccumulator(myDisposable);

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
    final long timeMs = System.currentTimeMillis();
    if (myAlarm != null) {
      if (myAlarm.isEmpty())
        myScheduleResizeMs.set(timeMs);
      myAlarm.cancelAllRequests();
      final double scale = myScale.getInverted();
      final int scaledW = CEIL.round(w * scale);
      final int scaledH = CEIL.round(h * scale);
      if (timeMs - myScheduleResizeMs.get() > RESIZE_DELAY_MS)
        myBrowser.wasResized(scaledW, scaledH);
      else
        myAlarm.addRequest(() -> {
          myBrowser.wasResized(scaledW, scaledH);
        }, RESIZE_DELAY_MS);
    }
  }

  @Override
  public InputMethodRequests getInputMethodRequests() {
    return myInputMethodAdapter;
  }

  @SuppressWarnings("DuplicatedCode")
  @Override
  protected void processMouseEvent(MouseEvent e) {
    super.processMouseEvent(e);
    if (e.isConsumed()) {
      return;
    }

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
    if (e.isConsumed()) {
      return;
    }

    if (TouchScrollUtil.isTouchScroll(e)) {
      myBrowser.sendTouchEvent(new CefTouchEvent(0, e.getX(), e.getY(), 0, 0, 0, 0, getTouchEventType(e), e.getModifiersEx(),
                                                 CefTouchEvent.PointerType.UNKNOWN));
    } else {
      myWheelEventsAccumulator.push(e);
    }
  }

  static CefTouchEvent.EventType getTouchEventType(MouseWheelEvent e) {
    if (!TouchScrollUtil.isTouchScroll(e)) return null;

    if (TouchScrollUtil.isBegin(e)) {
      return CefTouchEvent.EventType.PRESSED;
    }
    else if (TouchScrollUtil.isUpdate(e)) {
      return CefTouchEvent.EventType.MOVED;
    }
    else if (TouchScrollUtil.isEnd(e)) {
      return CefTouchEvent.EventType.RELEASED;
    }

    return CefTouchEvent.EventType.CANCELLED;
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

  class InputMethodAdapter implements InputMethodRequests, InputMethodListener {
    @Override
    public void inputMethodTextChanged(InputMethodEvent event) {
      int committedCharacterCount = event.getCommittedCharacterCount();

      AttributedCharacterIterator text = event.getText();
      if (text == null) {
        return;
      }
      char c = text.first();
      if (committedCharacterCount > 0) {
        StringBuilder textBuffer = new StringBuilder();
        while (committedCharacterCount-- > 0) {
          textBuffer.append(c);
          c = text.next();
        }

        String committedText = textBuffer.toString();
        // Vladimir.Kharitonov@jetbrains.com:
        // The second argument `replacementRange` is actually not needed. The invalid range shall be passed here. It's not possible because
        // of https://github.com/chromiumembedded/cef/issues/3422
        // To be fixed after updating CEF
        myBrowser.ImeCommitText(committedText, myRenderHandler.getSelectionRange(), 0);
      }

      StringBuilder textBuffer = new StringBuilder();
      while (c != CharacterIterator.DONE) {
        textBuffer.append(c);
        c = text.next();
      }

      var compositionText = textBuffer.toString();
      if (!compositionText.isEmpty()) {
        CefCompositionUnderline underline =
          new CefCompositionUnderline(new CefRange(0, compositionText.length()), new Color(0, true), new Color(0, true), 0,
                                      CefCompositionUnderline.Style.SOLID);
        // Vladimir.Kharitonov@jetbrains.com:
        // The third argument `replacementRange` is actually not needed. The invalid range shall be passed here. It's not possible because
        // of https://github.com/chromiumembedded/cef/issues/3422
        // To be fixed after updating CEF
        myBrowser.ImeSetComposition(compositionText, List.of(underline), myRenderHandler.getSelectionRange(),
                                    new CefRange(compositionText.length(), compositionText.length()));
      }
      event.consume();
    }

    @Override
    public void caretPositionChanged(InputMethodEvent event) { }

    @Override
    public Rectangle getTextLocation(TextHitInfo offset) {
      Rectangle[] boxes =
        ObjectUtils.notNull(myRenderHandler.getCompositionCharactersBBoxes(), new Rectangle[]{getDefaultImePositions()});
      Rectangle candidateWindowPosition = boxes.length == 0 ? getDefaultImePositions() : new Rectangle(boxes[0]);

      var componentLocation = getLocationOnScreen();
      candidateWindowPosition.translate(componentLocation.x, componentLocation.y);
      return candidateWindowPosition;
    }

    @Override
    public @Nullable TextHitInfo getLocationOffset(int x, int y) {
      Point p = new Point(x, y);
      var componentLocation = getLocationOnScreen();
      p.translate(-componentLocation.x, -componentLocation.y);

      Rectangle[] boxes = myRenderHandler.getCompositionCharactersBBoxes();
      if (boxes == null) {
        return null;
      }
      TextHitInfo result = null;
      for (int i = 0; i < boxes.length; i++) {
        Rectangle r = boxes[i];
        if (r.contains(p)) {
          result = TextHitInfo.leading(i);
          break;
        }
      }

      return result;
    }

    @Override
    public int getInsertPositionOffset() {
      return 0;
    }

    @Override
    public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex, AttributedCharacterIterator.Attribute[] attributes) {
      return new AttributedString("").getIterator();
    }

    @Override
    public int getCommittedTextLength() {
      return 0;
    }

    @Override
    public @Nullable AttributedCharacterIterator cancelLatestCommittedText(AttributedCharacterIterator.Attribute[] attributes) {
      myBrowser.ImeCancelComposing();
      return null;
    }

    @Override
    public @Nullable AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
      return new AttributedString(myRenderHandler.getSelectedText()).getIterator();
    }

    private Rectangle getDefaultImePositions() {
      return new Rectangle(0, getHeight(), 0, 0);
    }
  }

  /**
   * This class is an adapter between Java and CEF mouse wheel API.
   * CEF scrolling performance in some applications(e.g. PDF viewer) might be not enough to handle every java screen in time.
   * The purpose of this adapter is to reduce amount of events to handle by CEF.
   * MouseWheelEventsAccumulator accumulate wheel events(by X and Y axis independent) during the time defined by TIMEOUT_MS and sends
   * composed events to the browser on timeout or on reaching wheel rotation tolerance(defined by TOLERANCE).
   * <p>
   * In practice, this reduces the number of events by about 2 times
   */
  private class MouseWheelEventsAccumulator {
    private final Composition myCompositionX, myCompositionY;
    private final int wheelFactor = RegistryManager.getInstance().intValue("ide.browser.jcef.osr.wheelRotation.factor");
    public static final int TIMEOUT_MS = 500;
    public static final int TOLERANCE = 3;

    MouseWheelEventsAccumulator(Disposable parent) {
      myCompositionX = new Composition(parent);
      myCompositionY = new Composition(parent);
    }

    void push(MouseWheelEvent event) {
      if (event.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
        myBrowser.sendMouseWheelEvent(event);
        return;
      }

      Composition composition = event.isShiftDown() ? myCompositionX : myCompositionY;
      if (composition.lastEvent != null && !areHomogenous(composition.lastEvent, event)) {
        commit(composition);
        return;
      }

      if (composition.lastEvent == null) {
        composition.myAlarm.addRequest(() -> commit(composition), TIMEOUT_MS);
      }

      composition.pendingScrollAmount += event.getScrollAmount();
      composition.pendingPreciseWheelRotation += event.getPreciseWheelRotation();
      composition.pendingClickCount += event.getClickCount();
      composition.lastEvent = event;

      double scale = myScale.getIdeBiased();
      if (Math.abs(composition.pendingPreciseWheelRotation * wheelFactor) > TOLERANCE * scale) {
        commit(composition);
      }
    }

    private void commit(Composition composition) {
      if (composition.lastEvent == null) return;

      double val = composition.pendingPreciseWheelRotation * wheelFactor;
      if (SystemInfoRt.isLinux || SystemInfoRt.isMac) {
        val *= -1;
      }
      double scale = myScale.getIdeBiased();
      myBrowser.sendMouseWheelEvent(new MouseWheelEvent(
        composition.lastEvent.getComponent(),
        composition.lastEvent.getID(),
        composition.lastEvent.getWhen(),
        composition.lastEvent.getModifiersEx(),
        ROUND.round(composition.lastEvent.getX() / scale),
        ROUND.round(composition.lastEvent.getY() / scale),
        ROUND.round(composition.lastEvent.getXOnScreen() / scale),
        ROUND.round(composition.lastEvent.getYOnScreen() / scale),
        composition.pendingClickCount,
        composition.lastEvent.isPopupTrigger(),
        composition.lastEvent.getScrollType(),
        composition.pendingScrollAmount,
        (int)val,
        val));
      composition.reset();
    }

    static private boolean areHomogenous(MouseWheelEvent e1, MouseWheelEvent e2) {
      if (e1 == null || e2 == null) return false;

      double distance = Point2D.distance(e1.getX(), e1.getY(), e2.getX(), e2.getY());
      return e1.getComponent() == e2.getComponent() &&
             e1.getID() == e2.getID() &&
             e1.getModifiersEx() == e2.getModifiersEx() &&
             e1.isPopupTrigger() == e2.isPopupTrigger() &&
             e1.getScrollType() == e2.getScrollType() &&
             distance < TOLERANCE;
    }

    private static class Composition {
      private int pendingClickCount = 0;
      private int pendingScrollAmount = 0;
      private double pendingPreciseWheelRotation = 0.0;
      private final Alarm myAlarm;
      private @Nullable MouseWheelEvent lastEvent;

      Composition(Disposable parent) {
        myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
      }

      void reset() {
        lastEvent = null;
        pendingPreciseWheelRotation = 0;
        pendingClickCount = 0;
        pendingScrollAmount = 0;
        myAlarm.cancelAllRequests();
      }
    }
  }
}
