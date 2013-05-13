/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 4/8/13 7:29 PM
 */
public class ExternalSystemUiUtil {

  public static final int INSETS = 7;

  private ExternalSystemUiUtil() {
  }

  /**
   * Asks to show balloon that contains information related to the given component.
   *
   * @param component    component for which we want to show information
   * @param messageType  balloon message type
   * @param message      message to show
   */
  public static void showBalloon(@NotNull JComponent component, @NotNull MessageType messageType, @NotNull String message) {
    final BalloonBuilder builder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null)
      .setDisposable(ApplicationManager.getApplication())
      .setFadeoutTime(TimeUnit.SECONDS.toMillis(1));
    Balloon balloon = builder.createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }

  @NotNull
  public static GridBag getLabelConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().anchor(GridBagConstraints.WEST).weightx(0).insets(insets);
  }

  @NotNull
  public static GridBag getFillLineConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().weightx(1).coverLine().fillCellHorizontally().anchor(GridBagConstraints.WEST).insets(insets);
  }

  public static void fillBottom(@NotNull JComponent component) {
    component.add(Box.createVerticalGlue(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
  }
}
