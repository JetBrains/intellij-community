// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.Function;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ObjectUtils;
import com.intellij.util.RetinaImage;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.cef.JCefAppConfig;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.*;

/**
 * A render handler for an off-screen browser.
 *
 * @see JBCefOsrComponent
 * @author tav
 */
class JBCefOsrHandler implements CefRenderHandler {
  // [tav] todo: consider volatile image as alternative
  private @Nullable JBHiDPIScaledImage myImage;

  private final @NotNull JComponent myComponent;
  private final @NotNull Function<JComponent, Rectangle> myScreenBoundsProvider;
  private final @NotNull AtomicReference<Point> myLocationOnScreenRef = new AtomicReference<>(new Point());
  private final @NotNull JBCefOsrComponent.MyScale myScale = new JBCefOsrComponent.MyScale();

  private final @NotNull Object myImageLock = new Object();

  JBCefOsrHandler(@NotNull JBCefOsrComponent component, @Nullable Function<JComponent, Rectangle> screenBoundsProvider) {
    myComponent = component;
    component.setRenderHandler(this);
    myScreenBoundsProvider = ObjectUtils.notNull(screenBoundsProvider, JBCefOSRHandlerFactory.DEFAULT.createScreenBoundsProvider());

    myComponent.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        updateLocation();
      }
    });

    myComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        updateLocation();
      }
    });
  }

  @Override
  public Rectangle getViewRect(CefBrowser browser) {
    double scale = myScale.getIdeBiased();
    return new Rectangle(0, 0, CEIL.round(myComponent.getWidth() / scale), CEIL.round(myComponent.getHeight() / scale));
  }

  @Override
  public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
    Rectangle rect = myScreenBoundsProvider.fun(myComponent);
    screenInfo.Set(getDeviceScaleFactor(browser), 32, 4, false, rect, rect);
    return true;
  }

  @Override
  public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
    Point pt = viewPoint.getLocation();
    Point loc = myLocationOnScreenRef.get();
    pt.translate(loc.x, loc.y);
    return SystemInfoRt.isWindows ? scaleUp(pt) : pt;
  }

  @Override
  public double getDeviceScaleFactor(CefBrowser browser) {
    return JCefAppConfig.getDeviceScaleFactor(myComponent);
  }

  @Override
  public void onPopupShow(CefBrowser browser, boolean show) {
  }

  @Override
  public void onPopupSize(CefBrowser browser, Rectangle size) {
  }

  @Override
  public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
    synchronized (myImageLock) {
      Dimension size = getDevImageSize();
      if (size.width != width || size.height != height) {
        updateImage(width, height);
      }
      //noinspection ConstantConditions
      BufferedImage bi = (BufferedImage)myImage.getDelegate();
      @SuppressWarnings("ConstantConditions")
      int[] dst = ((DataBufferInt)bi.getRaster().getDataBuffer()).getData();
      IntBuffer src = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

      for (Rectangle rect : dirtyRects) {
        if (rect.width < width) {
          for (int line = rect.y; line < rect.y + rect.height; line++) {
            int offset = line * width + rect.x;
            src.position(offset).get(dst, offset, Math.min(rect.width, src.capacity() - offset));
          }
        }
        else { // optimized for a buffer wide dirty rect
          int offset = rect.y * width;
          src.position(offset).get(dst, offset, Math.min(rect.height * width, src.capacity() - offset));
        }
      }
    }
    myComponent.repaint(scaleDown(findOuterRect(dirtyRects)));
  }

  @Override
  public boolean onCursorChange(CefBrowser browser, int cursorType) {
    SwingUtilities.invokeLater(() -> myComponent.setCursor(new Cursor(cursorType)));
    return true;
  }

  @Override
  public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
    return false;
  }

  @Override
  public void updateDragCursor(CefBrowser browser, int operation) {
  }

  public void paint(Graphics2D g) {
    synchronized (myImageLock) {
      if (myImage != null) {
        // the graphics clip will optimize the whole image painting
        UIUtil.drawImage(g, myImage, 0, 0, null);
      }
    }
  }

  private static @NotNull Rectangle findOuterRect(Rectangle@NotNull[] rects) {
    if (rects.length == 1) return rects[0];

    int minX = 0;
    int minY = 0;
    int maxX = 0;
    int maxY = 0;
    for (Rectangle rect : rects) {
      if (rect.x < minX) minX = rect.x;
      if (rect.y < minY) minY = rect.y;
      int rX = rect.x + rect.width;
      if (rX > maxX) maxX = rX;
      int rY = rect.y + rect.height;
      if (rY > maxY) maxY = rY;
    }
    return new Rectangle(minX, minY, maxX - minX, maxY - minY);
  }

  public void updateScale(JBCefOsrComponent.MyScale scale) {
    myScale.update(scale);
  }

  private void updateLocation() {
    // getLocationOnScreen() is an expensive op, so do not request it on every mouse move, but cache
    myLocationOnScreenRef.set(myComponent.getLocationOnScreen());
  }

  private void updateImage(int width, int height) {
    synchronized (myImageLock) {
      Dimension size = getDevImageSize();
      if (size.width != width || size.height != height) {
        myImage = (JBHiDPIScaledImage)RetinaImage.createFrom(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), myScale.getJreBiased(), null);
      }
    }
  }

  private @NotNull Dimension getDevImageSize() {
    if (myImage == null) return new Dimension(0, 0);

    BufferedImage bi = (BufferedImage)myImage.getDelegate();
    //noinspection ConstantConditions
    return new Dimension(bi.getWidth(), bi.getHeight());
  }

  private @NotNull Rectangle scaleDown(@NotNull Rectangle rect) {
    double scale = myScale.getJreBiased();
    return new Rectangle(FLOOR.round(rect.x / scale), FLOOR.round(rect.y / scale),
                         CEIL.round(rect.width / scale), CEIL.round(rect.height / scale));
  }

  private @NotNull Point scaleUp(@NotNull Point pt) {
    double scale = myScale.getJreBiased();
    return new Point(ROUND.round(pt.x * scale), ROUND.round(pt.y * scale));
  }
}
