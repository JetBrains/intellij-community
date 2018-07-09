// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.concurrency.JobScheduler;
import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author ksafonov
 */
class IdeErrorsIcon extends JLabel {
  private final LayeredIcon myIcon;
  private final boolean myEnableBlink;

  private Future myBlinker;

  IdeErrorsIcon(boolean enableBlink) {
    myEnableBlink = enableBlink;
    setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));

    myIcon = new LayeredIcon(AllIcons.Ide.FatalError, AllIcons.Ide.FatalError_read, AllIcons.Ide.EmptyFatalError) {
      @Override
      public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        super.paintIcon(c, g, x, y);
      }

      @Override
      public synchronized void setLayerEnabled(int layer, boolean enabled) {
        super.setLayerEnabled(layer, enabled);
      }
    };
    setIcon(myIcon);
  }

  void setState(MessagePool.State state) {
    switch (state) {
      case UnreadErrors:
        myIcon.setLayerEnabled(0, true);
        myIcon.setLayerEnabled(1, false);
        myIcon.setLayerEnabled(2, false);
        startBlinker();
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(DiagnosticBundle.message("error.notification.tooltip"));
        break;

      case ReadErrors:
        stopBlinker();
        myIcon.setLayerEnabled(0, false);
        myIcon.setLayerEnabled(1, true);
        myIcon.setLayerEnabled(2, false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(DiagnosticBundle.message("error.notification.tooltip"));
        break;

      case NoErrors:
        stopBlinker();
        myIcon.setLayerEnabled(0, false);
        myIcon.setLayerEnabled(1, false);
        myIcon.setLayerEnabled(2, true);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        setToolTipText(null);
        break;
    }

    repaint();
  }

  private synchronized void startBlinker() {
    if (myEnableBlink && myBlinker == null) {
      myBlinker = JobScheduler.getScheduler().scheduleWithFixedDelay(new Runnable() {
        private boolean enabled = false;

        @Override
        public void run() {
          myIcon.setLayerEnabled(0, enabled);
          myIcon.setLayerEnabled(1, false);
          myIcon.setLayerEnabled(2, !enabled);
          repaint();
          enabled = !enabled;
        }
      }, 1, 1, TimeUnit.SECONDS);
    }
  }

  private synchronized void stopBlinker() {
    if (myBlinker != null) {
      myBlinker.cancel(true);
      myBlinker = null;
    }
  }
}