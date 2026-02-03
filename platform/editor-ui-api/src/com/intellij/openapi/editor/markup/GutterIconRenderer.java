// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.util.ui.accessibility.SimpleAccessible;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 * Represents an icon in the gutter area, including its actions.
 * Gutter icons are drawn to the left of the folding area
 * and can be used, for example, to mark implemented or overridden methods.
 * <p>
 * The daemon code analyzer checks a newly arrived gutter icon renderer
 * against the old one, and if they are equal, does not redraw the icon.
 * So it is highly advisable to override {@link #hashCode()}/{@link #equals(Object)}
 * to avoid icon flickering when the old gutter renderer gets replaced with the new one.
 * Proper implementation of {@code equals} is also important for renderers that specify
 * gutter icons for inlays, see {@link EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)}.
 * <p>
 * During indexing, methods are only invoked for renderers implementing {@link DumbAware}.
 *
 * @author max
 * @see RangeHighlighter#setGutterIconRenderer(GutterIconRenderer)
 * @see Inlay#getGutterIconRenderer()
 * @see EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)
 */
public abstract class GutterIconRenderer implements GutterMark, PossiblyDumbAware, SimpleAccessible {
  /**
   * Returns the action group actions that are used to fill the icon's context menu.
   *
   * @return the actions for the context menu, or null if no context menu is needed
   * @see #getRightButtonClickAction()
   */
  public @Nullable ActionGroup getPopupMenuActions() {
    return null;
  }

  /**
   * Returns the text of the tooltip displayed when the mouse hovers over the icon.
   *
   * @return the tooltip text, or null if no tooltip is needed
   */
  @Override
  public @Nullable String getTooltipText() {
    return null;
  }

  /**
   * Returns the action executed when the icon is left-clicked.
   *
   * @return the action instance, or null if there is no left-click action
   */
  public @Nullable AnAction getClickAction() {
    return null;
  }

  /**
   * Returns the action executed when the icon is middle-clicked.
   *
   * @return the action instance, or null if there is no middle-click action
   */
  public @Nullable AnAction getMiddleButtonClickAction() {
    return null;
  }

  /**
   * Returns the action executed when the icon is right-clicked.
   *
   * @return the action instance, or null to show the popup menu
   * @see #getPopupMenuActions()
   */
  public @Nullable AnAction getRightButtonClickAction() {
    return null;
  }

  /**
   * Returns whether the hand cursor should be displayed when the mouse is hovering over the icon.
   *
   * @return {@code true} to display the hand cursor, {@code false} to display the regular cursor
   */
  public boolean isNavigateAction() {
    return false;
  }

  /**
   * Defines positioning of the icon inside the gutter's icon area.
   * The order in which icons with the same alignment values are displayed is not specified
   * (it can be influenced using {@link com.intellij.openapi.editor.GutterMarkPreprocessor}).
   */
  public @NotNull Alignment getAlignment() {
    return Alignment.CENTER;
  }

  /**
   * Returns the callback that handles drag and drop of the gutter icon.
   *
   * @return the drag handler callback, or null if the icon does not support drag and drop.
   */
  public @Nullable GutterDraggableObject getDraggableObject() {
    return null;
  }

  // subclasses should override this method to provide localized name
  @SuppressWarnings("HardCodedStringLiteral")
  @Override
  public @NotNull String getAccessibleName() {
    return getAccessibleName(getIcon(), "icon: ");
  }

  public final @NonNls @NotNull String getFeatureId() {
    return getAccessibleName(getIcon(), "");
  }

  private static @NotNull String getAccessibleName(@Nullable Icon icon, @NotNull String prefix) {
    if (icon instanceof RetrievableIcon) {
      return getAccessibleName(((RetrievableIcon)icon).retrieveIcon(), prefix);
    }
    if (icon instanceof CompositeIcon) {
      StringBuilder b = new StringBuilder("composite icon: ");
      int count = ((CompositeIcon)icon).getIconCount();
      for (int i = 0; i < count; i++) {
        b.append(getAccessibleName(((CompositeIcon)icon).getIcon(i), ""));
        if (i < count - 1) b.append(" & ");
      }
      return b.toString();
    }
    if (icon instanceof CachedImageIcon) {
      String path = ((CachedImageIcon)icon).getOriginalPath();
      if (path != null) {
        String[] split = path.split(Pattern.quote("/") + "|" + Pattern.quote("\\"));
        String name = split[split.length - 1];
        return prefix + name.split(Pattern.quote("."))[0];
      }
    }
    return prefix + "unknown";
  }

  @Override
  public @Nullable String getAccessibleTooltipText() {
    return getTooltipText();
  }

  public enum Alignment {
    /**
     * A special alignment option to replace the line number with the icon.
     * Used for breakpoints in the New UI.
     */
    @ApiStatus.Internal
    LINE_NUMBERS(0),
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
