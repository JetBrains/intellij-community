// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.util;

import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import static com.intellij.openapi.util.registry.Registry.intValue;

/**
 * This helper class is intended to prevent opening a popup right after its closing.
 */
public class PopupState implements JBPopupListener, PopupMenuListener {
  private final long threshold = intValue("ide.popup.hide.show.threshold", 200);
  private boolean hidden = true;
  private long time;

  private void markAsShown() {
    hidden = false;
  }

  private void markAsHidden() {
    hidden = true;
    time = System.currentTimeMillis();
  }

  public boolean isRecentlyHidden() {
    if (!hidden) return false;
    hidden = false;
    return (System.currentTimeMillis() - time) < threshold;
  }

  public boolean isHidden() {
    return hidden;
  }

  // JBPopupListener

  @Override
  public void beforeShown(@NotNull LightweightWindowEvent event) {
    markAsShown();
  }

  @Override
  public void onClosed(@NotNull LightweightWindowEvent event) {
    markAsHidden();
  }

  // PopupMenuListener

  @Override
  public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
    markAsShown();
  }

  @Override
  public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
    markAsHidden();
  }

  @Override
  public void popupMenuCanceled(PopupMenuEvent event) {
  }
}
