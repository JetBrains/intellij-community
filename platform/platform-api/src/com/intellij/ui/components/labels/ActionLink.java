// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.labels;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 * @see <a href="https://jetbrains.github.io/ui/controls/link/">IJ Platform UI Guidelines | Link</a>
 * @deprecated use {@link com.intellij.ui.components.AnActionLink} instead
 */
@Deprecated(forRemoval = true)
public class ActionLink extends LinkLabel<Object> implements UiCompatibleDataProvider {
  private static final EmptyIcon ICON = JBUIScale.scaleIcon(EmptyIcon.create(0, 12));
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
    setListener(new LinkListener<>() {
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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    Point p = SwingUtilities.getRoot(this).getLocationOnScreen();
    sink.set(PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE, new Rectangle(p.x, p.y + getHeight(), 0, 0));
    sink.set(PlatformDataKeys.CONTEXT_MENU_POINT,
             SwingUtilities.convertPoint(this, 0, getHeight(), UIUtil.getRootPane(this)));
    DataSink.uiDataSnapshot(sink, myAction);
  }

  @TestOnly
  public AnAction getAction() {
    return myAction;
  }
}
