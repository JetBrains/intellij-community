/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Interface which should be implemented in order to draw icons in the gutter area and handle events
 * for them. Gutter icons are drawn to the left of the folding area and can be used, for example,
 * to mark implemented or overridden methods.
 *
 * @author max
 * @see RangeHighlighter#setGutterIconRenderer(GutterIconRenderer)
 */
public abstract class GutterIconRenderer {
  /**
   * Returns the icon drawn in the gutter.
   *
   * @return the gutter icon.
   */
  @NotNull
  public abstract Icon getIcon();

  /**
   * Returns the action group actions from which are used to fill the context menu
   * displayed when the icon is right-clicked.
   *
   * @return the group of actions for the context menu, or null if no context menu is required.
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

    private int myWeight;

    private Alignment(int weight) {
      myWeight = weight;
    }

    public int getWeight() {
      return myWeight;
    }
  }
}
