// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface which should be implemented in order to draw icons in the gutter area and handle events
 * for them. Gutter icons are drawn to the left of the folding area and can be used, for example,
 * to mark implemented or overridden methods.<p/>
 *
 * Daemon code analyzer checks newly arrived gutter icon renderer against the old one and if they are equal, does not redraw the icon.
 * So it is highly advisable to override hashCode()/equals() methods to avoid icon flickering when old gutter renderer gets replaced with the new.<p/>
 *
 * During indexing, methods are only invoked for renderers implementing {@link DumbAware}.
 *
 * @author max
 * @see RangeHighlighter#setGutterIconRenderer(GutterIconRenderer)
 */
public abstract class GutterIconRenderer implements GutterMark, PossiblyDumbAware {
  /**
   * Returns the action group actions from which are used to fill the context menu
   * displayed when the icon is right-clicked.
   *
   * @return the group of actions for the context menu, or null if no context menu is required.
   * @see #getRightButtonClickAction()
   */
  @Nullable
  public ActionGroup getPopupMenuActions() {
    return null;
  }

  /**
   * Returns the text of the tooltip displayed when the mouse is over the icon.
   *
   * @return the tooltip text, or null if no tooltip is required.
   */
  @Override
  @Nullable
  public String getTooltipText() {
    return null;
  }

  /**
   * Returns the action executed when the icon is left-clicked.
   *
   * @return the action instance, or null if no action is required.
   */
  @Nullable
  public AnAction getClickAction() {
    return null;
  }

  /**
   * Returns the action executed when the icon is middle-clicked.
   *
   * @return the action instance, or null if no action is required.
   */
  @Nullable
  public AnAction getMiddleButtonClickAction() {
    return null;
  }

  /**
   * Returns the action executed when the icon is right-clicked.
   *
   * @return the action instance, or null to show the popup menu
   * @see #getPopupMenuActions()
   */
  @Nullable
  public AnAction getRightButtonClickAction() {
    return null;
  }

  /**
   * Returns the value indicating whether the hand cursor should be displayed when the mouse
   * is hovering over the icon.
   *
   * @return true if the hand cursor should be displayed, false if the regular cursor is displayed.
   */
  public boolean isNavigateAction() {
    return false;
  }

  /**
   * Returns the priority of the icon relative to other icons. Multiple icons in the same line
   * are drawn in increasing priority order.
   *
   * @return the priority value.
   */
  @NotNull
  public Alignment getAlignment() {
    return Alignment.CENTER;
  }

  /**
   * Returns the callback which can be used to handle drag and drop of the gutter icon.
   *
   * @return the drag handler callback, or null if no drag and drop of the icon is required.
   */
  @Nullable
  public GutterDraggableObject getDraggableObject() {
    return null;
  }

  public enum Alignment {
    LEFT(1),
    RIGHT(3),
    CENTER(2);

    private final int myWeight;

    Alignment(int weight) {
      myWeight = weight;
    }

    public int getWeight() {
      return myWeight;
    }
  }

  @Override
  public abstract boolean equals(Object obj);
  @Override
  public abstract int hashCode();
}
