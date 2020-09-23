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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
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
  int calcWidthInPixels(@NotNull Inlay inlay);

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
  void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes);

  /**
   * Returns a registered id of action group, which is to be used for displaying context menu for the given custom element.
   * If {@code null} is returned (and {@link #getContextMenuGroup(Inlay)} also returns {@code null}), standard editor's context menu will be
   * displayed upon corresponding mouse event.
   */
  @Nullable
  @NonNls
  default String getContextMenuGroupId(@NotNull Inlay inlay) {
    return null;
  }

  /**
   * Returns an action group, which is to be used for the given custom element's context menu. If {@code null} is returned (and
   * {@link #getContextMenuGroupId(Inlay)} also returns {@code null}), standard editor's context menu will be displayed upon corresponding
   * mouse event.
   * <p>
   * This method takes preference over {@link #getContextMenuGroupId(Inlay)}, i.e. if it returns a non-null value, the latter method won't
   * be called.
   */
  @ApiStatus.Experimental
  @Nullable
  default ActionGroup getContextMenuGroup(@NotNull Inlay inlay) {
    return null;
  }


  /**
   * Allows to show an icon in gutter and process corresponding mouse events for block custom elements (other types of custom elements are
   * not supported at the moment). Icon will only be rendered if its height is not larger than the element's height.<p>
   * Returned provider should have a meaningful implementation of {@code equals} method - {@link Inlay#update()} will update the inlay's
   * provider (only) if newly returned instance is not equal to the previously defined one.
   */
  @ApiStatus.Experimental
  @Nullable
  default GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
    return null;
  }
}
