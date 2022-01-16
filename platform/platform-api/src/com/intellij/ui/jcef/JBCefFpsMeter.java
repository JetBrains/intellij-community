// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  public static final @NotNull JBCefFpsMeter NOOP = new JBCefFpsMeter() {
    @Override
    public void paintFrameStarted() {}
    @Override
    public void paintFrameFinished(@NotNull Graphics g) {}
    @Override
    public int getFps() {
      return 0;
    }
    @Override
    public void setActive(boolean active) {}
    @Override
    public boolean isActive() {
      return false;
    }
    @Override
    public void registerComponent(@NotNull Component component) {}
    @Override
    public boolean isAfterDeactivated() {
      return false;
    }
  };

  public abstract void paintFrameStarted();

  public abstract void paintFrameFinished(@NotNull Graphics g);

  public abstract int getFps();

  public abstract void setActive(boolean active);

  public abstract boolean isActive();

  public abstract void registerComponent(@NotNull Component component);

  public abstract boolean isAfterDeactivated();

  public static @NotNull JBCefFpsMeter register(@NotNull String id) {
    JBCefFpsMeter instance = INSTANCES.get(id);
    if (instance != null &&
        (!RegistryManager.getInstance().is("ide.browser.jcef.osr.measureFPS") || instance != NOOP))
    {
      return instance;
    }
    instance = RegistryManager.getInstance().is("ide.browser.jcef.osr.measureFPS") ? new JBCefFpsMeterImpl(id) : NOOP;
    INSTANCES.put(id, instance);
    return instance;
  }

  public static @NotNull JBCefFpsMeter get(@NotNull String id) {
    JBCefFpsMeter instance = INSTANCES.get(id);
    if (instance == null) {
      throw new IllegalStateException("JBCefOsrHandler.PerfStats not registered: " + id);
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
  private final @NotNull AtomicBoolean myIsAfterDeactivated = new AtomicBoolean();
  private final @NotNull Rectangle myFpsBarBounds = new Rectangle();
  private final @NotNull AtomicReference<Font> myFont = new AtomicReference<>();
  private final @NotNull AtomicReference<WeakReference<Component>> myComp = new AtomicReference<>(null);

  private static final int NS = 1000000000;
  private static final int TICK_NS = NS;

  @SuppressWarnings("unused")
  JBCefFpsMeterImpl(@NotNull String id) {
  }

  @Override
  public void paintFrameStarted() {
    if (myFrameCount.get() == 0) {
      myStartMeasureTime.set(System.nanoTime());
    }
  }

  @Override
  public void paintFrameFinished(@NotNull Graphics g) {
    myFrameCount.incrementAndGet();
    myMeasureDuration.set(System.nanoTime() - myStartMeasureTime.get());
    if (isActive()) {
      tick();
      drawFps(g);
    }
    if (myMeasureDuration.get() > NS) {
      myFrameCount.set(0);
    }
  }

  private void tick() {
    if (myMeasureDuration.get() > TICK_NS) {
      myFps.set((int)(myFrameCount.get() / ((float)myMeasureDuration.get() / NS)));
      // during the measurement the component can be repainted partially in which case
      // the FPS bar may run out of the clip, so here we request the whole component
      // repaint once per a tick
      requestRepaint();
    }
  }

  @Override
  public int getFps() {
    return Math.min(myFps.get(), 99);
  }

  private void drawFps(@NotNull Graphics g) {
    Graphics gr = g.create();
    try {
      gr.setColor(JBColor.blue);
      gr.fillRect(0, 0, JBUIScale.scale((int)myFpsBarBounds.getWidth()), JBUIScale.scale((int)myFpsBarBounds.getHeight()));
      gr.setColor(JBColor.green);
      gr.setFont(myFont.get());
      int fps = getFps();
      gr.drawString((fps == 0 ? "__" : fps) + " fps", 10, 10 + myFont.get().getSize());
    } finally {
      gr.dispose();
    }
  }

  @Override
  public void setActive(boolean active) {
    myIsActive.set(active);
    if (active) {
      reset();
    }
    else {
      myIsAfterDeactivated.set(true);
      requestRepaint();
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

  @Override
  public boolean isAfterDeactivated() {
    return myIsAfterDeactivated.getAndSet(false);
  }

  private void requestRepaint() {
    WeakReference<Component> compRef = myComp.get();
    if (compRef == null) return;
    Component comp = compRef.get();
    if (comp == null) return;
    comp.repaint();
  }

  private void reset() {
    myFrameCount.set(0);
    myMeasureDuration.set(0);

    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null) return;

    myFont.set(JBFont.create(new Font("Sans", Font.BOLD, 16)));
    focusOwner.getFontMetrics(myFont.get());
    myFpsBarBounds.setBounds(myFont.get().getStringBounds("00 fps", focusOwner.getFontMetrics(myFont.get()).getFontRenderContext()).getBounds());
    myFpsBarBounds.setSize(myFpsBarBounds.width + JBUIScale.scale(20), myFpsBarBounds.height + JBUIScale.scale(20));
  }
}