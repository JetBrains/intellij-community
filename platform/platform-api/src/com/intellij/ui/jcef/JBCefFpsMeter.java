// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.util.LazyInitializer;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Measures FPS. Use {@link com.intellij.internal.jcef.JBCefOsrBrowserMeasureFpsAction} to activate.
 *
 * @author tav
 */
@ApiStatus.Internal
public abstract class JBCefFpsMeter {
  private static final @NotNull Map<String, JBCefFpsMeter> INSTANCES = new HashMap<>(1);

  public abstract void paintFrameStarted();

  public abstract void paintFrameFinished(@NotNull Graphics g);

  public abstract int getFps();

  public abstract void setActive(boolean active);

  public abstract boolean isActive();

  public abstract void registerComponent(@NotNull Component component);

  public synchronized static @NotNull JBCefFpsMeter register(@NotNull String id) {
    JBCefFpsMeter instance = INSTANCES.get(id);
    if (instance != null) {
      return instance;
    }
    instance = new JBCefFpsMeterImpl(id);
    INSTANCES.put(id, instance);
    return instance;
  }

  public synchronized static @Nullable JBCefFpsMeter get(@NotNull String id) {
    JBCefFpsMeter instance = INSTANCES.get(id);
    if (instance == null) {
      Logger.getInstance(JBCefFpsMeter.class).warn(JBCefFpsMeter.class + " not registered: " + id);
    }
    return instance;
  }
}

class JBCefFpsMeterImpl extends JBCefFpsMeter {
  private final @NotNull AtomicInteger myFps = new AtomicInteger();
  private final @NotNull AtomicInteger myFrameCount = new AtomicInteger();
  private final @NotNull AtomicLong myStartMeasureTime = new AtomicLong();
  private final @NotNull AtomicLong myMeasureDuration = new AtomicLong();
  private final @NotNull AtomicBoolean myIsActive = new AtomicBoolean();
  private final @NotNull AtomicReference<Rectangle> myFpsBarBounds = new AtomicReference<>(new Rectangle());
  private final @NotNull AtomicReference<Font> myFont = new AtomicReference<>();
  private final @NotNull AtomicReference<WeakReference<Component>> myComp = new AtomicReference<>(null);
  private final @NotNull AtomicReference<Timer> myTimer = new AtomicReference<>();

  private static final int TICK_DELAY_MS = 1000;
  private static final int FPS_STR_OFFSET = 10;

  private static final LazyInitializer.LazyValue<@NotNull Component> DEFAULT_COMPONENT = LazyInitializer.create(() -> {
    Component comp = new JPanel();
    try {
      GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
      AWTAccessor.getComponentAccessor().setGraphicsConfiguration(comp, gc);
    } catch (HeadlessException ignore) {
    }
    return comp;
  });

  @SuppressWarnings("unused")
  JBCefFpsMeterImpl(@NotNull String id) {
  }

  @Override
  public void paintFrameStarted() {
  }

  @Override
  public void paintFrameFinished(@NotNull Graphics g) {
    if (isActive()) {
      myFrameCount.incrementAndGet();
      drawFps(g);
    }
  }

  private void tick() {
    if (myStartMeasureTime.get() > 0) {
      myMeasureDuration.set(System.nanoTime() - myStartMeasureTime.get());
      myFps.set((int)(myFrameCount.get() / ((float)myMeasureDuration.get() / 1000000000)));
    }
    myFrameCount.set(0);
    myStartMeasureTime.set(System.nanoTime());

    // during the measurement the component can be repainted partially in which case
    // the FPS bar may run out of the clip, so here we request repaint once per a tick
    requestFpsBarRepaint();
  }

  @Override
  public int getFps() {
    return Math.min(myFps.get(), 99);
  }

  private void drawFps(@NotNull Graphics g) {
    Graphics gr = g.create();
    try {
      gr.setColor(JBColor.blue);
      Rectangle r = myFpsBarBounds.get();
      gr.fillRect(r.x, r.y, r.width, r.height);
      gr.setColor(JBColor.green);
      gr.setFont(myFont.get());
      int fps = getFps();
      gr.drawString((fps == 0 ? "__" : fps) + " fps", FPS_STR_OFFSET, FPS_STR_OFFSET + myFont.get().getSize());
    } finally {
      gr.dispose();
    }
  }

  @Override
  public void setActive(boolean active) {
    boolean wasActive = myIsActive.getAndSet(active);
    if (active && !wasActive) {
      myTimer.set(new Timer(TICK_DELAY_MS, actionEvent -> tick()));
      myTimer.get().setRepeats(true);
      myTimer.get().start();
      reset();
    }
    else if (!active && wasActive) {
      myTimer.get().stop();
      myTimer.set(null);
      // clear the FPS bar
      requestFpsBarRepaint();
    }
  }

  @Override
  public boolean isActive() {
    return myIsActive.get();
  }

  @Override
  public void registerComponent(@NotNull Component component) {
    myComp.set(new WeakReference<>(component));
  }

  private void requestFpsBarRepaint() {
    Rectangle r = myFpsBarBounds.get();
    getComponent().repaint(r.x, r.y, r.width, r.height);
  }

  private @NotNull Component getComponent() {
    Component comp = null;
    WeakReference<Component> compRef = myComp.get();
    if (compRef != null) {
      comp = compRef.get();
    }
    return comp != null ? comp : DEFAULT_COMPONENT.get();
  }

  private void reset() {
    myFps.set(0);
    myFrameCount.set(0);
    myStartMeasureTime.set(0);
    myMeasureDuration.set(0);

    Component comp = getComponent();
    myFont.set(JBFont.create(new Font("Sans", Font.BOLD, 16)));
    comp.getFontMetrics(myFont.get());
    Rectangle strBounds = myFont.get().getStringBounds("00 fps", comp.getFontMetrics(myFont.get()).getFontRenderContext()).getBounds();
    myFpsBarBounds.get().setBounds(0, 0, strBounds.width + FPS_STR_OFFSET * 2, strBounds.height + FPS_STR_OFFSET * 2);
  }
}