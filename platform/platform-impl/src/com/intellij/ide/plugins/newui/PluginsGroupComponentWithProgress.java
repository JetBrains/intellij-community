// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@ApiStatus.Internal
public abstract class PluginsGroupComponentWithProgress extends PluginsGroupComponent {
  private static final Logger LOG = Logger.getInstance(PluginsGroupComponentWithProgress.class);

  private AsyncProcessIcon myIcon = new AsyncProcessIcon.BigCentered(IdeBundle.message("progress.text.loading"));
  private @Nullable Runnable myVisibleRunnable;

  public PluginsGroupComponentWithProgress(@NotNull EventHandler eventHandler) {
    super(eventHandler);
    myIcon.setOpaque(false);
    myIcon.setPaintPassiveIcon(false);
    add(myIcon);
    myIcon.resume();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    updateIconLocation();
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    updateIconLocation();
  }

  private void updateIconLocation() {
    if (myIcon != null && myIcon.isVisible()) {
      myIcon.updateLocation(this);
    }
  }

  public void startLoading() {
    LOG.info("Marketplace tab: loading started");
    if (myIcon != null) {
      myIcon.setVisible(true);
      myIcon.resume();
      fullRepaint();
    }
  }

  public void stopLoading() {
    LOG.info("Marketplace tab: loading stopped");
    if (myIcon != null) {
      myIcon.suspend();
      myIcon.setVisible(false);
      fullRepaint();
    }
  }

  private void fullRepaint() {
    doLayout();
    revalidate();
    repaint();
  }

  public void dispose() {
    if (myIcon != null) {
      remove(myIcon);
      myIcon.dispose();
      myIcon = null;
    }
  }

  @Override
  public void clear() {
    super.clear();
    if (myIcon != null) {
      add(myIcon);
    }
  }

  public void setVisibleRunnable(@NotNull Runnable visibleRunnable) {
    myVisibleRunnable = visibleRunnable;
  }

  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    if (aFlag && myVisibleRunnable != null) {
      Runnable runnable = myVisibleRunnable;
      myVisibleRunnable = null;
      runnable.run();
    }
  }
}