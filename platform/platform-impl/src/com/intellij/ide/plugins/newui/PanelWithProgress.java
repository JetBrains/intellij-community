// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class PanelWithProgress extends JBPanelWithEmptyText {
  private AsyncProcessIcon myIcon = new AsyncProcessIcon.Big("Panel.Loading") {
    @NotNull
    @Override
    protected Rectangle calculateBounds(@NotNull JComponent container) {
      Dimension size = container.getSize();
      Dimension iconSize = getPreferredSize();
      return new Rectangle((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height);
    }
  };

  public PanelWithProgress(@NotNull String emptyText) {
    getEmptyText().setText(emptyText);
  }

  public void addProgress() {
    myIcon.setOpaque(false);
    myIcon.setPaintPassiveIcon(false);
    add(myIcon);

    stopLoading();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myIcon != null && ScreenUtil.isStandardAddRemoveNotify(this)) {
      remove(myIcon);
      Disposer.dispose(myIcon);
      myIcon = null;
    }
  }

  @Override
  public void doLayout() {
    super.doLayout();
    if (myIcon != null) {
      myIcon.updateLocation(this);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myIcon != null) {
      myIcon.updateLocation(this);
    }
  }

  public void startLoading() {
    if (myIcon != null) {
      myIcon.setVisible(true);
      myIcon.resume();
      doLayout();
      revalidate();
      repaint();
    }
  }

  public void stopLoading() {
    if (myIcon != null) {
      myIcon.suspend();
      myIcon.setVisible(false);
      doLayout();
      revalidate();
      repaint();
    }
  }

  public void scrollToBegin() {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (getComponentCount() > 0) {
        scrollRectToVisible(getComponent(0).getBounds());
      }
    });
  }
}