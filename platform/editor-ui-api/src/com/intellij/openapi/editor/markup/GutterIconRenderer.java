// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.CompositeIcon;
import com.intellij.util.ui.accessibility.SimpleAccessible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Pattern;

/**
 * Interface which should be implemented in order to draw icons in the gutter area and handle events
 * for them. Gutter icons are drawn to the left of the folding area and can be used, for example,
 * to mark implemented or overridden methods.<p/>
 *
 * Daemon code analyzer checks newly arrived gutter icon renderer against the old one and if they are equal, does not redraw the icon.
 * So it is highly advisable to override hashCode()/equals() methods to avoid icon flickering when old gutter renderer gets replaced with
 * the new. Proper implementation of {@code equals} is also important for instances used to specify gutter icons for inlays
 * (see {@link EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)})<p/>
 *
 * During indexing, methods are only invoked for renderers implementing {@link DumbAware}.
 *
 * @author max
 * @see RangeHighlighter#setGutterIconRenderer(GutterIconRenderer)
 * @see Inlay#getGutterIconRenderer()
 * @see EditorCustomElementRenderer#calcGutterIconRenderer(Inlay)
 */
public abstract class GutterIconRenderer implements GutterMark, PossiblyDumbAware, SimpleAccessible {
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
   * Defines positioning of the icon inside gutter's icon area. The order, in which icons with the same alignment values are displayed, is
   * not specified (it can be influenced using {@link com.intellij.openapi.editor.GutterMarkPreprocessor}).
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

  @Override
  @NotNull
  public String getAccessibleName() {
    return getAccessibleName(getIcon(), "icon: ");
  }

  public final String getFeatureId() {
    return getAccessibleName(getIcon(), "");
  }

  private static String getAccessibleName(@Nullable Icon icon, @NotNull String prefix) {
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
    if (icon instanceof IconLoader.CachedImageIcon) {
      String path = ((IconLoader.CachedImageIcon)icon).getOriginalPath();
      if (path != null) {
        String[] split = path.split(Pattern.quote("/") + "|" + Pattern.quote("\\"));
        String name = split[split.length - 1];
        return prefix + name.split(Pattern.quote("."))[0];
      }
    }
    return prefix + "unknown";
  }

  @Nullable
  @Override
  public String getAccessibleTooltipText() {
    return getTooltipText();
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
