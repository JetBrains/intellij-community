// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public class ActionLink extends LinkLabel<Object> implements DataProvider {
  private static final EmptyIcon ICON = JBUI.scale(EmptyIcon.create(0, 12));
  private final AnAction myAction;
  private InputEvent myEvent;
  private Color myVisitedColor;
  private Color myActiveColor;
  private Color myNormalColor;

  public ActionLink(@NlsContexts.LinkLabel String text, @NotNull AnAction action) {
    this(text, ICON, action);
  }

  public ActionLink(@NlsContexts.LinkLabel String text, Icon icon, @NotNull AnAction action) {
    this(text, icon, action, null, ActionPlaces.UNKNOWN);
  }

  public ActionLink(@NlsContexts.LinkLabel String text,
                    Icon icon,
                    @NotNull AnAction action,
                    @Nullable Runnable onDone,
                    @NotNull String place) {
    super(text, icon);
    setListener(new LinkListener<Object>() {
      @Override
      public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
        ActionUtil.invokeAction(myAction, ActionLink.this, place, myEvent, onDone);
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

  @Override
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
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.is(dataId)) {
      final Point p = SwingUtilities.getRoot(this).getLocationOnScreen();
      return new Rectangle(p.x, p.y + getHeight(), 0, 0);
    }
    if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      return SwingUtilities.convertPoint(this, 0, getHeight(), UIUtil.getRootPane(this));
    }
    return myAction instanceof DataProvider ? ((DataProvider)myAction).getData(dataId) : null;
  }

  @TestOnly
  public AnAction getAction() {
    return myAction;
  }
}
