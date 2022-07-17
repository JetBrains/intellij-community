// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @see JBPopupFactory
 */
public interface Balloon extends Disposable, PositionTracker.Client<Balloon>, LightweightWindow {

  String KEY = "Balloon.property";

  void show(PositionTracker<Balloon> tracker, Position preferredPosition);

  void show(RelativePoint target, Position preferredPosition);

  void show(JLayeredPane pane);

  void showInCenterOf(JComponent component);

  Dimension getPreferredSize();

  void setBounds(Rectangle bounds);

  void addListener(@NotNull JBPopupListener listener);

  void hide();
  void hide(boolean ok);

  void setAnimationEnabled(boolean enabled);

  boolean wasFadedIn();
  boolean wasFadedOut();

  boolean isDisposed();

  void setTitle(String title);

  default void setId(String id) {}

  enum Position {
    below, above, atLeft, atRight
  }

  enum Layer {
    normal, top
  }

}
