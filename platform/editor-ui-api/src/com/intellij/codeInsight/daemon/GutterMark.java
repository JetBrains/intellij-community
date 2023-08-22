// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Get an icon drawn in the gutter area, to the left of the folding area.
 * Example gutter marks are for implemented or overridden methods.
 * <p>
 * The daemon code analyzer checks any newly arrived gutter icon renderer
 * against the old one and if they are equal, does not redraw the icon.
 * So it is highly advisable to override hashCode()/equals() methods
 * to avoid icon flickering when an old gutter renderer gets replaced with a new one.
 * <p>
 * During indexing, methods are only invoked for renderers
 * that implement {@link com.intellij.openapi.project.DumbAware}.
 *
 * @see com.intellij.openapi.editor.markup.RangeHighlighter#setGutterIconRenderer(com.intellij.openapi.editor.markup.GutterIconRenderer)
 */
public interface GutterMark {
  /** Returns the icon that is drawn in the gutter. */
  @NotNull Icon getIcon();

  /** Returns the tooltip text, if any, that is displayed when the mouse is over the icon. */
  @Nullable @NlsContexts.Tooltip String getTooltipText();
}
