/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
