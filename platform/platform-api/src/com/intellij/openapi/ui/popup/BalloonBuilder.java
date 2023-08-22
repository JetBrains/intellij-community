// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionListener;

/**
 * @see JBPopupFactory#createBalloonBuilder(javax.swing.JComponent)
 */
public interface BalloonBuilder {
  @NotNull
  BalloonBuilder setBorderColor(@NotNull Color color);

  @NotNull
  BalloonBuilder setBorderInsets(@Nullable Insets insets);

  @NotNull
  BalloonBuilder setFillColor(@NotNull Color color);

  @NotNull
  BalloonBuilder setHideOnClickOutside(boolean hide);

  @NotNull
  BalloonBuilder setHideOnKeyOutside(boolean hide);

  @NotNull
  BalloonBuilder setShowCallout(boolean show);

  @NotNull
  BalloonBuilder setCloseButtonEnabled(boolean enabled);

  @NotNull
  BalloonBuilder setFadeoutTime(long fadeoutTime);

  @NotNull
  BalloonBuilder setAnimationCycle(int time);

  @NotNull
  BalloonBuilder setHideOnFrameResize(boolean hide);

  @NotNull
  BalloonBuilder setHideOnLinkClick(boolean hide);

  @NotNull
  BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick);

  @NotNull
  BalloonBuilder setCalloutShift(int length);

  @NotNull
  BalloonBuilder setPositionChangeXShift(int positionChangeXShift);

  @NotNull
  BalloonBuilder setPositionChangeYShift(int positionChangeYShift);

  @NotNull
  BalloonBuilder setHideOnAction(boolean hideOnAction);

  @NotNull
  BalloonBuilder setDialogMode(boolean dialogMode);

  @NotNull
  BalloonBuilder setTitle(@Nullable @PopupTitle String title);

  @NotNull
  BalloonBuilder setContentInsets(Insets insets);

  @NotNull
  BalloonBuilder setShadow(boolean shadow);

  @NotNull
  BalloonBuilder setSmallVariant(boolean smallVariant);

  @NotNull
  BalloonBuilder setLayer(Balloon.Layer layer);

  @NotNull
  BalloonBuilder setBlockClicksThroughBalloon(boolean block);

  @NotNull
  BalloonBuilder setRequestFocus(boolean requestFocus);

  @NotNull
  default BalloonBuilder setPointerSize(Dimension size) { return this; }

  @NotNull
  default BalloonBuilder setCornerToPointerDistance(int distance) { return this; }

  default BalloonBuilder setCornerRadius(int radius) { return this; }

  BalloonBuilder setHideOnCloseClick(boolean hideOnCloseClick);

  /**
   * Links target balloon life cycle to the given object. I.e. current balloon will be auto-hide and collected as soon
   * as given anchor is disposed.
   * <p/>
   * <b>Note:</b> given disposable anchor is assumed to correctly implement {@link #hashCode()} and {@link #equals(Object)}.
   *
   * @param anchor  target anchor to link to
   * @return        balloon builder which produces balloon linked to the given object life cycle
   */
  @NotNull
  BalloonBuilder setDisposable(@NotNull Disposable anchor);

  @NotNull
  Balloon createBalloon();
}
