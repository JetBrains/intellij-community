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
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.accessibility.SimpleAccessible;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Interface which should be implemented in order to paint custom markers in the line
 * marker area (over the folding area) and process mouse events on the markers.
 *
 * @author max
 */
public interface ActiveGutterRenderer extends LineMarkerRenderer, SimpleAccessible {
  /**
   * Returns the text of the tooltip displayed when the mouse is over the renderer area.
   *
   * @return the tooltip text, or null if no tooltip is required.
   */
  @Nullable
  default @NlsContexts.Tooltip String getTooltipText() {
    return null;
  }

  /**
   * Processes a mouse released event on the marker.
   * <p>
   * Implementations must extend one of {@link #canDoAction} methods, otherwise the action will never be called.
   *
   * @param editor the editor to which the marker belongs.
   * @param e      the mouse event instance.
   */
  void doAction(@NotNull Editor editor, @NotNull MouseEvent e);

  /**
   * @return true if {@link #doAction(Editor, MouseEvent)} should be called
   */
  default boolean canDoAction(@NotNull Editor editor, @NotNull MouseEvent e) {
    return canDoAction(e);
  }

  default boolean canDoAction(@NotNull MouseEvent e) {
    return false;
  }

  @NotNull
  @Override
  default String getAccessibleName() {
    return PlatformEditorBundle.message("accessible.name.marker.unknown");
  }

  @Nullable
  @Override
  default String getAccessibleTooltipText() {
    return getTooltipText();
  }

  /**
   * Calculates the rectangular bounds enclosing the marker.
   * Returns null if the marker is not rendered for the provided line.
   *
   * @param editor the editor the renderer belongs to
   * @param lineNum the line which the marker should intersect
   * @param preferredBounds the preferred bounds to take into account
   * @return the new calculated bounds or the preferred bounds or null
   */
  @Nullable
  default Rectangle calcBounds(@NotNull Editor editor, int lineNum, @NotNull Rectangle preferredBounds) {
    return preferredBounds;
  }
}
