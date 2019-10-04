/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * An interface, defining size and representation of custom visual element in editor.
 *
 * @see InlayModel#addInlineElement(int, boolean, EditorCustomElementRenderer)
 * @see InlayModel#addBlockElement(int, boolean, boolean, int, EditorCustomElementRenderer)
 * @see InlayModel#addAfterLineEndElement(int, boolean, EditorCustomElementRenderer)
 * @see Inlay#getRenderer()
 */
public interface EditorCustomElementRenderer {
  /**
   * Renderer implementation should override this to define width of custom element (in pixels). Returned value will define the result of
   * {@link Inlay#getWidthInPixels()} and the width of {@code targetRegion} parameter passed to renderer's
   * {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)} method. For inline and after-line-end elements it should always be
   * a positive value.
   */
  default int calcWidthInPixels(@NotNull Inlay inlay) {
    return calcWidthInPixels(inlay.getEditor());
  }

  /**
   * @deprecated Override/use {@link #calcWidthInPixels(Inlay)} instead. This method will be removed.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  default int calcWidthInPixels(@NotNull Editor editor) {
    throw new RuntimeException("Method not implemented");
  }

  /**
   * Block element's renderer implementation can override this method to defines the height of element (in pixels). If not overridden,
   * element's height will be equal to editor's line height. Returned value will define the result of {@link Inlay#getWidthInPixels()} and
   * the height of {@code targetRegion} parameter passed to renderer's {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)} method.
   * Returned value is currently not used for inline elements.
   */
  default int calcHeightInPixels(@NotNull Inlay inlay) {
    return inlay.getEditor().getLineHeight();
  }

  /**
   * Renderer implementation should override this to define the appearance of custom element.
   * 
   * @param targetRegion region where painting should be performed, location of this rectangle is calculated by editor implementation,
   *                     dimensions of the rectangle match element's width and height (provided by {@link #calcWidthInPixels(Inlay)}
   *                     and {@link #calcHeightInPixels(Inlay)})
   * @param textAttributes attributes of surrounding text
   */
  default void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
    paint(inlay.getEditor(), g, targetRegion, textAttributes);
  }

  /**
   * @deprecated Override/use {@link #paint(Inlay, Graphics, Rectangle, TextAttributes)} instead. This method will be removed.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  default void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
    throw new RuntimeException("Method not implemented");
  }

  /**
   * Returns a registered id of action group, which is to be used for displaying context menu for the given custom element.
   * If {@code null} is returned, standard editor's context menu will be displayed upon corresponding mouse event.
   */
  @Nullable
  default String getContextMenuGroupId(@NotNull Inlay inlay) {
    return getContextMenuGroupId();
  }

  /**
   * @deprecated Override/use {@link #getContextMenuGroupId(Inlay)} instead. This method will be removed.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  default String getContextMenuGroupId() {
    return null;
  }
}
