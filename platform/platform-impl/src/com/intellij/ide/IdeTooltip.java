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
package com.intellij.ide;

import com.intellij.openapi.ui.popup.Balloon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class IdeTooltip {

  private Component myComponent;
  private Point myPoint;

  private Balloon.Position myPreferredPosition;

  private JComponent myTipComponent;

  private boolean myToCenter = false;
  private boolean myToCenterIfSmall = true;

  public IdeTooltip(Component component, Point point, JComponent tipComponent) {
    myComponent = component;
    myPoint = point;
    myTipComponent = tipComponent;
    setPreferredPosition(Balloon.Position.above);
  }

  public IdeTooltip setPreferredPosition(Balloon.Position position) {
    myPreferredPosition = position;
    return this;
  }

  public Component getComponent() {
    return myComponent;
  }

  public Point getPoint() {
    return myPoint;
  }

  public Balloon.Position getPreferredPosition() {
    return myPreferredPosition;
  }

  public JComponent getTipComponent() {
    return myTipComponent;
  }

  public IdeTooltip setToCenter(boolean toCenter) {
    myToCenter = toCenter;
    return this;
  }

  public boolean isToCenter() {
    return myToCenter;
  }

  public boolean isToCenterIfSmall() {
    return myToCenterIfSmall;
  }

  public IdeTooltip setToCenterIfSmall(boolean mayCenter) {
    myToCenterIfSmall = mayCenter;
    return this;
  }

  protected boolean canAutohideOn(MouseEvent me, boolean isInsideBalloon) {
    return true;
  }

  protected void onHidden() {

  }

  protected boolean beforeShow() {
    return true;
  }

  public void hide() {
    IdeTooltipManager.getInstance().hide(this);
  }

  public boolean canBeDismissedOnTimeout() {
    return true;
  }
}

