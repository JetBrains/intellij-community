// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.focus;

import com.intellij.util.ui.GraphicsUtil;

import javax.swing.JFrame;
import javax.swing.RootPaneContainer;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.function.Consumer;

public final class Util {
  static void drawOnActiveFrameGraphics(Consumer<? super Graphics2D> consumer) {
    Arrays.stream(Frame.getFrames()).
      filter(window -> window instanceof RootPaneContainer).
            filter(f -> f.isActive()).
            map(window -> (RootPaneContainer)window).
            filter(w -> w instanceof JFrame).
            filter(f -> f.getRootPane() != null).
            filter(f -> f.getGlassPane() != null).
            filter(window -> window.getRootPane() != null).
            map(window -> (window).getGlassPane()).
            map(jGlassPane -> GraphicsUtil.safelyGetGraphics(jGlassPane)).
            filter(g -> g != null).
            forEach(graphics -> {
              Graphics glassPaneGraphics = graphics.create();
              try {
                consumer.accept((Graphics2D)glassPaneGraphics);
              } finally {
                glassPaneGraphics.dispose();
              }
            });
  }
}
