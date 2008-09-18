package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

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
  Balloon createBalloon();

}