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
package com.intellij.codeInsight.daemon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Interface which should be implemented in order to draw icons in the gutter area.
 * Gutter icons are drawn to the left of the folding area and can be used, for example,
 * to mark implemented or overridden methods.
 *
 * Daemon code analyzer checks newly arrived gutter icon renderer against the old one and if they are equal, does not redraw the icon.
 * So it is highly advisable to override hashCode()/equals() methods to avoid icon flickering when old gutter renderer gets replaced with the new.
 *
 * During indexing, methods are only invoked for renderers implementing {@link com.intellij.openapi.project.DumbAware}.
 *
 * @see RangeHighlighter#setGutterIconRenderer(GutterIconRenderer)
 */
public interface GutterMark {
  /**
   * Returns the icon drawn in the gutter.
   *
   * @return the gutter icon.
   */
  @NotNull
  Icon getIcon();

  /**
   * Returns the text of the tooltip displayed when the mouse is over the icon.
   *
   * @return the tooltip text, or null if no tooltip is required.
   */
  @Nullable
  String getTooltipText();
}
