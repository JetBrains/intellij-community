// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Graphics;

@ApiStatus.Internal
public abstract class PluginsGroupComponentWithProgress extends PluginsGroupComponent {
  private static final Logger LOG = Logger.getInstance(PluginsGroupComponentWithProgress.class);

  private AsyncProcessIcon myLoadingIcon = new AsyncProcessIcon.BigCentered(IdeBundle.message("progress.text.loading"));
  private @Nullable Runnable myOnBecomingVisibleCallback;

  public PluginsGroupComponentWithProgress(@NotNull EventHandler eventHandler) {
    super(eventHandler);
    myLoadingIcon.setOpaque(false);
    myLoadingIcon.setPaintPassiveIcon(false);
    add(myLoadingIcon);
    myLoadingIcon.resume();
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
    if (myLoadingIcon != null && myLoadingIcon.isVisible()) {
      myLoadingIcon.updateLocation(this);
    }
  }

  public void showLoadingIcon() {
    LOG.debug("Marketplace tab: loading started");
    if (myLoadingIcon != null) {
      myLoadingIcon.setVisible(true);
      myLoadingIcon.resume();
      fullRepaint();
    }
  }

  public void hideLoadingIcon() {
    LOG.debug("Marketplace tab: loading stopped");
    if (myLoadingIcon != null) {
      myLoadingIcon.suspend();
      myLoadingIcon.setVisible(false);
      fullRepaint();
    }
  }

  private void fullRepaint() {
    doLayout();
    revalidate();
    repaint();
  }

  public void dispose() {
    if (myLoadingIcon != null) {
      remove(myLoadingIcon);
      Disposer.dispose(myLoadingIcon);
      myLoadingIcon = null;
    }
  }

  @Override
  public void clear() {
    super.clear();
    if (myLoadingIcon != null) {
      add(myLoadingIcon);
    }
  }

  public void setOnBecomingVisibleCallback(@NotNull Runnable onVisibilityChangeCallbackOnce) {
    myOnBecomingVisibleCallback = onVisibilityChangeCallbackOnce;
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible && myOnBecomingVisibleCallback != null) {
      Runnable runnable = myOnBecomingVisibleCallback;
      myOnBecomingVisibleCallback = null;
      runnable.run();
    }
  }
}