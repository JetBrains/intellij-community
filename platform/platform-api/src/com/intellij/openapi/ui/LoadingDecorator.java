/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LoadingDecorator {

  JLayeredPane myPane;

  LoadingLayer myLoadingLayer;
  Animator myFadeOutAnimator;

  int myDelay;
  Alarm myStartAlarm;
  boolean myStartRequest;


  public LoadingDecorator(JComponent content, Disposable parent, int startDelayMs) {
    this(content, parent, startDelayMs, false);
  }

  public LoadingDecorator(JComponent content, Disposable parent, int startDelayMs, boolean useMinimumSize) {
    myPane = new MyLayeredPane(useMinimumSize ? content : null);
    myLoadingLayer = new LoadingLayer();
    myDelay = startDelayMs;
    myStartAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, parent);

    setLoadingText("Loading...");


    myFadeOutAnimator = new Animator("Loading", 10, 500, false) {
      public void paintNow(final int frame, final int totalFrames, final int cycle) {
        myLoadingLayer.setAlpha(1f - ((float)frame) / ((float)totalFrames));
      }

      @Override
      protected void paintCycleEnd() {
        myLoadingLayer.setVisible(false);
        myLoadingLayer.setAlpha(-1);
      }
    };
    Disposer.register(parent, myFadeOutAnimator);


    myPane.add(content, JLayeredPane.DEFAULT_LAYER, 0);
    myPane.add(myLoadingLayer, JLayeredPane.DRAG_LAYER, 1);

    Disposer.register(parent, myLoadingLayer.myProgress);
  }

  protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
    parent.setLayout(new GridBagLayout());

    final Font font = text.getFont();
    text.setFont(font.deriveFont(font.getStyle(), font.getSize() + 8));
    //text.setForeground(Color.black);

    final int gap = new JLabel().getIconTextGap();
    final NonOpaquePanel result = new NonOpaquePanel(new FlowLayout(FlowLayout.CENTER, gap * 3, 0));
    result.add(icon);
    result.add(text);
    parent.add(result);

    return result;
  }

  public JComponent getComponent() {
    return myPane;
  }

  public void startLoading(final boolean takeSnapshot) {
    if (isLoading() || myStartRequest || myStartAlarm.isDisposed()) return;

    myStartRequest = true;
    if (myDelay > 0) {
      myStartAlarm.addRequest(new Runnable() {
        public void run() {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            public void run() {
              if (!myStartRequest) return;
              _startLoading(takeSnapshot);
            }
          });
        }
      }, myDelay);
    } else {
      _startLoading(takeSnapshot);
    }
  }

  private void _startLoading(final boolean takeSnapshot) {
    myLoadingLayer.setVisible(true, takeSnapshot);
  }

  public void stopLoading() {
    myStartRequest = false;
    myStartAlarm.cancelAllRequests();

    if (!isLoading()) return;

    myLoadingLayer.setVisible(false, false);
  }


  public String getLoadingText() {
    return myLoadingLayer.myText.getText();
  }

  public void setLoadingText(final String loadingText) {
    myLoadingLayer.myText.setText(loadingText);
  }

  public boolean isLoading() {
    return myLoadingLayer.isVisible();
  }

  private class LoadingLayer extends JPanel {
    private final JLabel myText = new JLabel("", SwingConstants.CENTER);

    private BufferedImage mySnapshot;
    private Color mySnapshotBg;

    private final AsyncProcessIcon myProgress = new AsyncProcessIcon.Big("Loading");

    private boolean myVisible;

    private float myCurrentAlpha;
    private final NonOpaquePanel myTextComponent;

    private LoadingLayer() {
      setOpaque(false);
      setVisible(false);
      myProgress.setOpaque(false);
      myTextComponent = customizeLoadingLayer(this, myText, myProgress);
      myProgress.suspend();
    }

    public void setVisible(final boolean visible, boolean takeSnapshot) {
      if (myVisible == visible) return;

      if (myVisible && !visible && myCurrentAlpha != -1) return;

      myVisible = visible;
      if (myVisible) {
        setVisible(myVisible);
        myCurrentAlpha = -1;
      }

      if (myVisible) {
        if (takeSnapshot && getWidth() > 0 && getHeight() > 0) {
          mySnapshot = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
          final Graphics2D g = mySnapshot.createGraphics();
          myPane.paint(g);
          final Component opaque = UIUtil.findNearestOpaque(this);
          mySnapshotBg = opaque != null ? opaque.getBackground() : UIUtil.getPanelBackground();
          g.dispose();
        }
        myProgress.resume();
      } else {
        disposeSnapshot();
        myProgress.suspend();

        myFadeOutAnimator.reset();
        myFadeOutAnimator.resume();
      }
    }

    private void disposeSnapshot() {
      if (mySnapshot != null) {
        mySnapshot.flush();
        mySnapshot = null;
      }
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (mySnapshot != null) {
        if (mySnapshot.getWidth() == getWidth() && mySnapshot.getHeight() == getHeight()) {
          g.drawImage(mySnapshot, 0, 0, getWidth(), getHeight(), null);
          g.setColor(new Color(200, 200, 200, 240));
          g.fillRect(0, 0, getWidth(), getHeight());
          return;
        } else {
          disposeSnapshot();
        }
      }

      if (mySnapshotBg != null) {
        g.setColor(mySnapshotBg);
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    }

    public void setAlpha(final float alpha) {
      myCurrentAlpha = alpha;
      paintImmediately(myTextComponent.getBounds());
    }

    @Override
    protected void paintChildren(final Graphics g) {
      if (myCurrentAlpha != -1) {
        ((Graphics2D)g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myCurrentAlpha));
      }
      super.paintChildren(g);
    }
  }

  private static class MyLayeredPane extends JBLayeredPane {
    private final JComponent myContent;

    private MyLayeredPane(JComponent content) {
      myContent = content;
    }

    @Override
    public Dimension getMinimumSize() {
      return myContent != null && !isMinimumSizeSet()
             ? myContent.getMinimumSize()
             : super.getMinimumSize();
    }

    @Override
    public Dimension getPreferredSize() {
      return myContent != null && !isPreferredSizeSet()
             ? myContent.getPreferredSize()
             : super.getPreferredSize();
    }

    @Override
    public void doLayout() {
      super.doLayout();
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        if (each instanceof Icon) {
          each.setBounds(0, 0, each.getWidth(), each.getHeight());
        } else {
          each.setBounds(0, 0, getWidth(), getHeight());
        }
      }
    }
  }
}
