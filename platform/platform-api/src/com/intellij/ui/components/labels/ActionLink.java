/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components.labels;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class ActionLink extends LinkLabel implements DataProvider {
  private static final EmptyIcon ICON = JBUI.scale(EmptyIcon.create(0, 12));
  private final AnAction myAction;
  private String myPlace = ActionPlaces.UNKNOWN;
  private InputEvent myEvent;
  private Color myVisitedColor;
  private Color myActiveColor;
  private Color myNormalColor;

  public ActionLink(String text, @NotNull AnAction action) {
    this(text, ICON, action);
  }

  public ActionLink(String text, Icon icon, @NotNull AnAction action) {
    this(text, icon, action, null);
  }

  public ActionLink(String text, Icon icon, @NotNull AnAction action, @Nullable final Runnable onDone) {
    super(text, icon);
    setListener(new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        ActionUtil.invokeAction(myAction, myEvent, ActionLink.this, myPlace, onDone);
      }
    }, null);
    myAction = action;
  }

  @Override
  public void doClick(InputEvent e) {
    myEvent = e;
    super.doClick();
  }

  @Override
  protected Color getVisited() {
    return myVisitedColor == null ? super.getVisited() : myVisitedColor;
  }

  public Color getActiveColor() {
    return myActiveColor == null ? super.getActive() : myActiveColor;
  }

  protected Color getTextColor() {
    return myUnderline ? getActiveColor() : getNormal();
  }

  @Override
  protected Color getNormal() {
    return myNormalColor == null ? super.getNormal() : myNormalColor;
  }

  public void setVisitedColor(Color visitedColor) {
    myVisitedColor = visitedColor;
  }

  public void setActiveColor(Color activeColor) {
    myActiveColor = activeColor;
  }

  public void setNormalColor(Color normalColor) {
    myNormalColor = normalColor;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
      final Point p = SwingUtilities.getRoot(this).getLocationOnScreen();
      return new Rectangle(p.x, p.y + getHeight(), 0, 0);
    }
    if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      return SwingUtilities.convertPoint(this, 0, getHeight(), UIUtil.getRootPane(this));
    }

    return null;
  }

  @TestOnly
  public AnAction getAction() {
    return myAction;
  }
}
