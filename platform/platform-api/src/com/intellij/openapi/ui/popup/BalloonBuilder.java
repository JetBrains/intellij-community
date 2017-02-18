/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
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
  BalloonBuilder setTitle(@Nullable String title);

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
