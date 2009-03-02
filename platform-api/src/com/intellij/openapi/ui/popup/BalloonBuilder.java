package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.ActionListener;

public interface BalloonBuilder {

  @NotNull
  BalloonBuilder setPreferredPosition(Balloon.Position position);

  @NotNull
  BalloonBuilder setBorderColor(@NotNull Color color);

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
  BalloonBuilder setHideOnFrameResize(boolean hide);

  @NotNull
  Balloon createBalloon();

  @NotNull
  BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick);

}