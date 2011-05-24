/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.PositionTracker;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * @see com.intellij.openapi.ui.popup.JBPopupFactory
 */
public interface Balloon extends Disposable {

  String KEY = "Balloon.property";

  void show(PositionTracker<Balloon> tracker, Position preferredPosition);

  void show(RelativePoint target, Position preferredPosition);

  void show(JLayeredPane pane);

  void showInCenterOf(JComponent component);

  Dimension getPreferredSize();

  void setBounds(Rectangle bounds);

  void addListener(JBPopupListener listener);

  void hide();

  void setAnimationEnabled(boolean enabled);

  boolean wasFadedIn();
  boolean wasFadedOut();

  boolean isDisposed();

  enum Position {
    below, above, atLeft, atRight
  }

}