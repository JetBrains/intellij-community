// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Interface which should be implemented in order to draw custom text annotations in the editor gutter.
 *
 * @author max
 * @author Konstantin Bulenkov
 * @see EditorGutter#registerTextAnnotation(TextAnnotationGutterProvider)
 */
public interface TextAnnotationGutterProvider {
  /**
   * Returns the text which should be drawn for the line with the specified number in the specified editor.
   *
   * @param line   the line for which the text is requested.
   * @param editor the editor in which the text will be drawn.
   * @return the text to draw, or null if no text should be drawn.
   */
  @Nullable String getLineText(int line, Editor editor);

  @Nullable @NlsContexts.Tooltip String getToolTip(int line, Editor editor);

  EditorFontType getStyle(int line, Editor editor);

  @Nullable ColorKey getColor(int line, Editor editor);

  /**
   * Returns the background color for the text.
   *
   * @param line the line for which the background color is requested.
   * @param editor the editor in which the text will be drawn.
   * @return the text to draw, or null if no text should be drawn.
   */
  @Nullable Color getBgColor(int line, Editor editor);

  /***
   * Enables annotation view modifications.
   */
  List<AnAction> getPopupActions(final int line, final Editor editor);

  /**
   * Called when the annotations are removed from the editor gutter.
   *
   * @see EditorGutter#closeAllAnnotations()
   */
  void gutterClosed();

  /**
   * If {@code true}, a couple of pixels will be added at both sides of displayed text (if it's not empty),
   * otherwise the width of annotation will be equal to the width of provided text.
   */
  default boolean useMargin() {
    return true;
  }
}
