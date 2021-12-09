// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
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
import java.awt.event.*;
import java.awt.image.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.*;

/**
 * A render handler for an off-screen browser.
 *
 * @see JBCefOsrComponent
 * @author tav
 */
class JBCefOsrHandler implements CefRenderHandler {
  private final @NotNull JComponent myComponent;
  private final @NotNull Function<JComponent, Rectangle> myScreenBoundsProvider;
  private final @NotNull AtomicReference<Point> myLocationOnScreenRef = new AtomicReference<>(new Point());
  private final @NotNull JBCefOsrComponent.MyScale myScale = new JBCefOsrComponent.MyScale();
  private final @NotNull JBCefFpsMeter myFpsMeter = JBCefFpsMeter.register(
    RegistryManager.getInstance().get("ide.browser.jcef.osr.measureFPS.id").asString());

  private volatile @Nullable JBHiDPIScaledImage myImage;
  private volatile @Nullable VolatileImage myVolatileImage;

  private final static @NotNull Point ZERO_POINT = new Point();
  private final static @NotNull Rectangle ZERO_RECT = new Rectangle();

  // jcef thread only
  private @NotNull Rectangle myPopupBounds = ZERO_RECT;
  private boolean myPopupShown;

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

    myFpsMeter.registerComponent(myComponent);
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
    Point loc = getLocation();
    if (SystemInfoRt.isMac) {
      Rectangle rect = myScreenBoundsProvider.fun(myComponent);
      pt.setLocation(loc.x + pt.x, rect.height - loc.y - pt.y);
    }
    else {
      pt.translate(loc.x, loc.y);
    }
    return SystemInfoRt.isWindows ? scaleUp(pt) : pt;
  }

  @Override
  public double getDeviceScaleFactor(CefBrowser browser) {
    return JCefAppConfig.getDeviceScaleFactor(myComponent);
  }

  @Override
  public void onPopupShow(CefBrowser browser, boolean show) {
    myPopupShown = show;
  }

  @Override
  public void onPopupSize(CefBrowser browser, Rectangle size) {
    myPopupBounds = scaleUp(size);
  }

  @Override
  public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
    JBHiDPIScaledImage image = myImage;
    VolatileImage volatileImage = myVolatileImage;

    //
    // Recreate images when necessary
    //
    if (!popup) {
      Dimension size = getDevImageSize();
      if (size.width != width || size.height != height) {
        image = (JBHiDPIScaledImage)RetinaImage.createFrom(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE), myScale.getJreBiased(), null);
        volatileImage = myComponent.createVolatileImage(width, height);
        dirtyRects = new Rectangle[]{new Rectangle(0, 0, width, height)};
      }
    }
    assert image != null;
    BufferedImage bufferedImage = (BufferedImage)image.getDelegate();
    assert bufferedImage != null;

    int imageWidth = bufferedImage.getWidth();
    int imageHeight = bufferedImage.getHeight();

    // {volatileImage} can be null if myComponent is not yet displayed, in that case we will use {myImage} in {paint(Graphics)} as
    // it can be called (asynchronously) when {myComponent} has already been displayed - in order not to skip the {onPaint} request
    if (volatileImage != null && volatileImage.contentsLost()) {
      int result = volatileImage.validate(myComponent.getGraphicsConfiguration());
      if (result != VolatileImage.IMAGE_OK) {
        dirtyRects = new Rectangle[]{ new Rectangle(0, 0, width, height) };
      }
      if (result == VolatileImage.IMAGE_INCOMPATIBLE) {
        volatileImage = myComponent.createVolatileImage(imageWidth, imageHeight);
      }
    }

    int[] dst = ((DataBufferInt)bufferedImage.getRaster().getDataBuffer()).getData();
    IntBuffer src = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

    //
    // Adjust the dirty rects for a popup case (a workaround for not enough erase rect after popup close)
    //
    if (!popup && !myPopupShown && myPopupBounds != ZERO_RECT) {
      // first repaint after popup close
      var rects = new ArrayList<>(Arrays.asList(dirtyRects));
      rects.add(myPopupBounds);
      Rectangle outerRect = findOuterRect(rects.toArray(new Rectangle[0]));
      // mind the bounds of the {buffer}
      outerRect = outerRect.intersection(new Rectangle(0, 0, width, height));
      dirtyRects = new Rectangle[]{ outerRect };
      myPopupBounds = ZERO_RECT;
    }

    //
    // Copy pixels into the BufferedImage
    //
    Point popupLoc = popup ? myPopupBounds.getLocation() : ZERO_POINT;

    for (Rectangle rect : dirtyRects) {
      if (rect.width < imageWidth) {
        for (int line = rect.y; line < rect.y + rect.height; line++) {
          int srcOffset = line * width + rect.x;
          int dstOffset = (line + popupLoc.y) * imageWidth + (rect.x + popupLoc.x);
          src.position(srcOffset).get(dst, dstOffset, Math.min(rect.width, src.capacity() - srcOffset));
        }
      }
      else { // optimized for a buffer wide dirty rect
        int srcOffset = rect.y * width;
        int dstOffset = (rect.y + popupLoc.y) * imageWidth;
        src.position(srcOffset).get(dst, dstOffset, Math.min(rect.height * width, src.capacity() - srcOffset));
      }
    }

    //
    // Draw the BufferedImage into the VolatileImage
    //
    Rectangle outerRect = findOuterRect(dirtyRects);
    if (popup) outerRect.translate(popupLoc.x, popupLoc.y);

    if (volatileImage != null) {
      Graphics2D viGr = (Graphics2D)volatileImage.getGraphics().create();
      try {
        double sx = viGr.getTransform().getScaleX();
        double sy = viGr.getTransform().getScaleY();
        viGr.scale(1 / sx, 1 / sy);
        viGr.drawImage(bufferedImage,
                       outerRect.x, outerRect.y, outerRect.x + outerRect.width, outerRect.y + outerRect.height,
                       outerRect.x, outerRect.y, outerRect.x + outerRect.width, outerRect.y + outerRect.height,
                       null);
      }
      finally {
        viGr.dispose();
      }
    }
    myImage = image;
    myVolatileImage = volatileImage;
    SwingUtilities.invokeLater(() -> myComponent.repaint(popup ? scaleDown(new Rectangle(0, 0, imageWidth, imageHeight)) : scaleDown(outerRect)));
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
    // The dirty rects passed to onPaint are set as the clip on the graphics, so here we draw the whole image.
    myFpsMeter.paintFrameStarted();
    Image volatileImage = myVolatileImage;
    Image image = myImage;
    if (volatileImage != null) {
      g.drawImage(volatileImage, 0, 0, null );
    }
    //
    else if (image != null) {
      UIUtil.drawImage(g, image, 0, 0, null);
    }
    myFpsMeter.paintFrameFinished(g);
  }

  private static @NotNull Rectangle findOuterRect(Rectangle@NotNull[] rects) {
    if (rects.length == 1) return rects[0];

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
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

  private @NotNull Point getLocation() {
    return myLocationOnScreenRef.get().getLocation();
  }

  private @NotNull Dimension getDevImageSize() {
    JBHiDPIScaledImage image = myImage;
    if (image == null) return new Dimension(0, 0);

    BufferedImage bi = (BufferedImage)image.getDelegate();
    assert bi != null;
    return new Dimension(bi.getWidth(), bi.getHeight());
  }

  private @NotNull Rectangle scaleDown(@NotNull Rectangle rect) {
    double scale = myScale.getJreBiased();
    return new Rectangle(FLOOR.round(rect.x / scale), FLOOR.round(rect.y / scale),
                         CEIL.round(rect.width / scale), CEIL.round(rect.height / scale));
  }

  private @NotNull Rectangle scaleUp(@NotNull Rectangle rect) {
    double scale = myScale.getJreBiased();
    return new Rectangle(FLOOR.round(rect.x * scale), FLOOR.round(rect.y * scale),
                         CEIL.round(rect.width * scale), CEIL.round(rect.height * scale));
  }

  private @NotNull Point scaleUp(@NotNull Point pt) {
    double scale = myScale.getJreBiased();
    return new Point(ROUND.round(pt.x * scale), ROUND.round(pt.y * scale));
  }
}
