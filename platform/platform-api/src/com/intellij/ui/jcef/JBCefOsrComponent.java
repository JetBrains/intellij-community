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

  private @NotNull AtomicLong mySchedlueResizeMs = new AtomicLong(-1);
  private @Nullable Alarm myAlarm;
  private @NotNull Disposable myDisposable;

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
        mySchedlueResizeMs.set(timeMs);
      myAlarm.cancelAllRequests();
      final double scale = myScale.getInverted();
      final int scaledW = CEIL.round(w * scale);
      final int scaledH = CEIL.round(h * scale);
      if (timeMs - mySchedlueResizeMs.get() > RESIZE_DELAY_MS)
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
      CefTouchEvent.EventType type;
      if (TouchScrollUtil.isBegin(e)) {
        type = CefTouchEvent.EventType.PRESSED;
      }
      else if (TouchScrollUtil.isUpdate(e)) {
        type = CefTouchEvent.EventType.MOVED;
      }
      else if (TouchScrollUtil.isEnd(e)) {
        type = CefTouchEvent.EventType.RELEASED;
      } else {
        type = CefTouchEvent.EventType.CANCELLED;
      }
      myBrowser.sendTouchEvent(new CefTouchEvent(0, e.getX(), e.getY(), 0, 0, 0, 0, type, e.getModifiersEx(),
                                                 CefTouchEvent.PointerType.UNKNOWN));
      return;
    }

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
      System.out.println("cancelLatestCommittedText");
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
}
