// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.Gray;
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
import org.cef.misc.CefRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.CEIL;
import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A render handler for an off-screen browser.
 *
 * @author tav
 * @see JBCefOsrComponent
 */
class JBCefOsrHandler implements CefRenderHandler {
  private final @NotNull JComponent myComponent;
  private final @NotNull Function<? super JComponent, ? extends Rectangle> myScreenBoundsProvider;
  private final @NotNull AtomicReference<Point> myLocationOnScreenRef = new AtomicReference<>(new Point());
  private final @NotNull JBCefOsrComponent.MyScale myScale = new JBCefOsrComponent.MyScale();
  private final @NotNull JBCefFpsMeter myFpsMeter = JBCefFpsMeter.register(
    RegistryManager.getInstance().get("ide.browser.jcef.osr.measureFPS.id").asString());

  private volatile @Nullable JBHiDPIScaledImage myImage;

  private volatile @Nullable JBHiDPIScaledImage myPopupImage;
  private volatile boolean myPopupShown = false;
  private volatile @NotNull Rectangle myPopupBounds = new Rectangle();
  private final Object myPopupMutex = new Object();

  private volatile @Nullable VolatileImage myVolatileImage;
  private volatile boolean myContentOutdated = false;
  private volatile CefRange mySelectionRange = new CefRange(0, 0);
  private volatile String mySelectedText = "";
  private volatile @Nullable Rectangle[] myCompositionCharactersBBoxes;

  JBCefOsrHandler(@NotNull JBCefOsrComponent component, @Nullable Function<? super JComponent, ? extends Rectangle> screenBoundsProvider) {
    myComponent = component;
    component.setRenderHandler(this);
    myScreenBoundsProvider = ObjectUtils.notNull(screenBoundsProvider, JBCefOSRHandlerFactory.DEFAULT.createScreenBoundsProvider());

    myComponent.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        // track got visible event
        if (myComponent.isShowing()) updateLocation();
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
    return SystemInfoRt.isWindows ? toRealCoordinates(pt) : pt;
  }

  @Override
  public double getDeviceScaleFactor(CefBrowser browser) {
    return JCefAppConfig.getDeviceScaleFactor(myComponent);
  }

  @Override
  public void onPopupShow(CefBrowser browser, boolean show) {
    synchronized (myPopupMutex) {
      myPopupShown = show;
    }
  }

  @Override
  public void onPopupSize(CefBrowser browser, Rectangle size) {
    synchronized (myPopupMutex) {
      myPopupBounds = size;
    }
  }

  @Override
  public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
    myFpsMeter.onPaintStarted();
    JBHiDPIScaledImage image = popup ? myPopupImage : myImage;

    Dimension size = getRealImageSize(image);
    if (size.width != width || size.height != height) {
      image = (JBHiDPIScaledImage)RetinaImage.createFrom(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE),
                                                         myScale.getJreBiased(), null);
    }

    assert image != null;
    if (popup) {
      synchronized (myPopupMutex) {
        drawByteBuffer(image, buffer, dirtyRects);
        myPopupImage = image;
      }
    }
    else {
      drawByteBuffer(image, buffer, dirtyRects);
      myImage = image;
    }

    myContentOutdated = true;
    SwingUtilities.invokeLater(() -> {
      if (!myComponent.isShowing()) return;
      JRootPane root = myComponent.getRootPane();
      RepaintManager rm = RepaintManager.currentManager(root);
      Rectangle dirtySrc = new Rectangle(0, 0, myComponent.getWidth(), myComponent.getHeight());
      Rectangle dirtyDst = SwingUtilities.convertRectangle(myComponent, dirtySrc, root);
      int dx = 1;
      // NOTE: should mark area outside browser (otherwise background component won't be repainted)
      rm.addDirtyRegion(root, dirtyDst.x - dx, dirtyDst.y - dx, dirtyDst.width + dx * 2, dirtyDst.height + dx * 2);
    });

    { // notify fps-meter
      long pixCount = 0;
      for (Rectangle r : dirtyRects)
        pixCount += (long)r.width * r.height;
      myFpsMeter.onPaintFinished(pixCount);
    }
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

  @Override
  public void OnImeCompositionRangeChanged(CefBrowser browser, CefRange selectionRange, Rectangle[] characterBounds) {
    this.mySelectionRange = selectionRange;
    this.myCompositionCharactersBBoxes = characterBounds;
  }

  @Override
  public void OnTextSelectionChanged(CefBrowser browser, String selectedText, CefRange selectionRange) {
    this.mySelectedText = selectedText;
    this.mySelectionRange = selectionRange;
  }

  public void paint(Graphics2D g) {
    if (!myComponent.isShowing()) {
      return;
    }

    myFpsMeter.paintFrameStarted();
    VolatileImage vi = myVolatileImage;

    do {
      boolean contentOutdated = myContentOutdated;
      myContentOutdated = false;
      if (vi == null || vi.getWidth() != myComponent.getWidth() || vi.getHeight() != myComponent.getHeight()) {
        vi = createVolatileImage();
      }
      else if (contentOutdated) {
        drawVolatileImage(vi);
      }

      switch (vi.validate(myComponent.getGraphicsConfiguration())) {
        case VolatileImage.IMAGE_RESTORED -> drawVolatileImage(vi);
        case VolatileImage.IMAGE_INCOMPATIBLE -> vi = createVolatileImage();
      }

      g.drawImage(vi, 0, 0, null);
    }
    while (vi.contentsLost());

    myVolatileImage = vi;

    myFpsMeter.paintFrameFinished(g);
  }

  public void updateScale(JBCefOsrComponent.MyScale scale) {
    myScale.update(scale);
  }

  private void updateLocation() {
    // getLocationOnScreen() is an expensive op, so do not request it on every mouse move, but cache
    myLocationOnScreenRef.set(myComponent.getLocationOnScreen());
  }

  public CefRange getSelectionRange() {
    return mySelectionRange;
  }

  public @Nullable Rectangle[] getCompositionCharactersBBoxes() {
    return myCompositionCharactersBBoxes;
  }

  public String getSelectedText() {
    return mySelectedText;
  }

  private @NotNull Point getLocation() {
    return myLocationOnScreenRef.get().getLocation();
  }

  private static @NotNull Dimension getRealImageSize(JBHiDPIScaledImage image) {
    if (image == null) return new Dimension(0, 0);
    BufferedImage bi = (BufferedImage)image.getDelegate();
    assert bi != null;
    return new Dimension(bi.getWidth(), bi.getHeight());
  }

  private @NotNull Point toRealCoordinates(@NotNull Point pt) {
    double scale = myScale.getJreBiased();
    return new Point(ROUND.round(pt.x * scale), ROUND.round(pt.y * scale));
  }

  private void drawContent(Graphics2D g) {
    Image image = myImage;
    if (image != null) {
      UIUtil.drawImage(g, image, 0, 0, null);
    }

    if (myPopupShown) {
      synchronized (myPopupMutex) {
        Image popupImage = myPopupImage;
        if (myPopupShown && popupImage != null) {
          UIUtil.drawImage(g, popupImage, myPopupBounds.x, myPopupBounds.y, null);
        }
      }
    }
  }

  private static void drawByteBuffer(@NotNull JBHiDPIScaledImage dst, @NotNull ByteBuffer src, Rectangle[] rectangles) {
    BufferedImage image = (BufferedImage)dst.getDelegate();
    assert image != null;
    int[] dstData = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
    IntBuffer srcData = src.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
    for (Rectangle rect : rectangles) {
      if (rect.width < image.getWidth()) {
        for (int line = rect.y; line < rect.y + rect.height; line++) {
          int offset = line * image.getWidth() + rect.x;
          srcData.position(offset).get(dstData, offset, Math.min(rect.width, src.capacity() - offset));
        }
      }
      else { // optimized for a buffer wide dirty rect
        int offset = rect.y * image.getWidth();
        srcData.position(offset).get(dstData, offset, Math.min(rect.height * image.getWidth(), src.capacity() - offset));
      }
    }
  }

  private void drawVolatileImage(VolatileImage vi) {
    Graphics2D g = (Graphics2D)vi.getGraphics().create();
    try {
      g.setBackground(Gray.TRANSPARENT);
      g.setComposite(AlphaComposite.Src);
      g.clearRect(0, 0, myComponent.getWidth(), myComponent.getHeight());

      drawContent(g);
    }
    finally {
      g.dispose();
    }
  }

  private VolatileImage createVolatileImage() {
    VolatileImage image = myComponent.getGraphicsConfiguration()
      .createCompatibleVolatileImage(myComponent.getWidth(), myComponent.getHeight(), Transparency.TRANSLUCENT);
    drawVolatileImage(image);
    return image;
  }
}
