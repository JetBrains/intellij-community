// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.awt.Point;

/**
 * A component that can provide popup placement information for itself or delegate popup placement to another component.
 */
public interface PopupOwner {
  /**
   * Returns the preferred popup location relative to this component.
   * <p>
   * This value is used when {@link #getPopupComponent()} returns {@code null} or this component itself.
   * Returning {@code null} means that no preferred location is available and the caller should use its own fallback
   * placement logic.
   *
   * @return the preferred popup location relative to this component, or {@code null} if there is no preferred location
   */
  @Nullable
  Point getBestPopupPosition();

  /**
   * Returns the component whose popup placement should be used instead of this component.
   * <p>
   * If the returned component also implements {@code PopupOwner}, callers may continue resolving popup placement
   * recursively. Returning {@code null} or this component means that this component's
   * {@link #getBestPopupPosition()} should be used.
   *
   * @return the component to use for popup placement, or {@code null} to use this component's own popup position
   */
  default @Nullable JComponent getPopupComponent() {
    return null;
  }
}
