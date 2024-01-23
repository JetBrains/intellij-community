// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

final class ChangeLAFAnimator {
  private float myAlpha = 1;
  private final Map<JLayeredPane, JComponent> myMap;
  private final Animator myAnimator;

  static ChangeLAFAnimator showSnapshot() {
    return new ChangeLAFAnimator();
  }

  private ChangeLAFAnimator() {
    myAnimator = new Animator("ChangeLAF", 60, 800, false) {
      @Override
      public void resume() {
        doPaint();
        super.resume();
      }

      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        myAlpha = 1 - (float)(1 - Math.cos(Math.PI * frame / (float)totalFrames)) / 2;
        doPaint();
      }
      @Override
      protected void paintCycleEnd() {
        if (!isDisposed()) {
          Disposer.dispose(this);
        }
      }

      @Override
      public void dispose() {
        try {
          super.dispose();
          for (Map.Entry<JLayeredPane, JComponent> entry : myMap.entrySet()) {
            JLayeredPane layeredPane = entry.getKey();
            layeredPane.remove(entry.getValue());
            layeredPane.revalidate();
            layeredPane.repaint();
          }
        } finally {
          myMap.clear();
        }
      }
    };

    Window[] windows = Window.getWindows();
    myMap = new LinkedHashMap<>();
    for (Window window : windows) {
      if (window instanceof RootPaneContainer rootPaneContainer && window.isShowing()) {
        Rectangle bounds = window.getBounds();
        JLayeredPane layeredPane = rootPaneContainer.getLayeredPane();
        BufferedImage image =
          ImageUtil.createImage(window.getGraphicsConfiguration(), bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics imageGraphics = image.getGraphics();
        GraphicsUtil.setupAntialiasing(imageGraphics);
        ((RootPaneContainer)window).getRootPane().paint(imageGraphics);

        JComponent imageLayer = new JComponent() {

          @Override
          public void paint(Graphics g) {
            g = g.create();
            ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
            UIUtil.drawImage(g, image, 0, 0, this);
          }

          @Override
          public Rectangle getBounds() {
            return layeredPane.getBounds();
          }
        };
        imageLayer.setSize(layeredPane.getSize());
        layeredPane.add(imageLayer, JLayeredPane.DRAG_LAYER);
        myMap.put(layeredPane, imageLayer);
      }
    }
    doPaint();
  }

  void hideSnapshotWithAnimation() {
    myAnimator.resume();
  }


  private void doPaint() {
    for (Map.Entry<JLayeredPane, JComponent> entry : myMap.entrySet()) {
      if (entry.getKey().isShowing()) {
        entry.getValue().revalidate();
        entry.getValue().repaint();
      }
    }
  }
}
