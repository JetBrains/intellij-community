/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.editor;

import java.awt.*;

/**
 * Interface for executing actions when text annotations in the editor gutter are clicked.
 *
 * @author lesya
 * @since 5.1
 * @see EditorGutter#registerTextAnnotation(TextAnnotationGutterProvider, EditorGutterAction)
 */
public interface EditorGutterAction {
  /**
   * Processes the click on the specified line.
   *
   * @param lineNum the number of line in the document the annotation for which was clicked.
   */
  void doAction(int lineNum);

  /**
   * Returns the cursor to be shown where the mouse is over the annotation for the specified line.
   *
   * @param lineNum the line number.
   * @return the cursor to show.
   */
  Cursor getCursor(final int lineNum);
}
