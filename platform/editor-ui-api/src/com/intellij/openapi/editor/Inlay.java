/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NotNull;

/**
 * A custom visual element displayed in editor. It is associated with a certain position in a document, but is not
 * represented in document text in any way. Inlay's document position (offset) is updated on document changes just like
 * for a {@link RangeMarker}. Inlay becomes invalid on explicit disposal, or when a document range fully containing inlay's offset,
 * is deleted.
 * <p>
 * WARNING! This is an experimental API, it can change at any time.
 */
public interface Inlay extends Disposable, UserDataHolderEx {
  /**
   * Tells whether this element is valid. Inlay becomes invalid on explicit disposal,
   * or when a document range fully containing inlay's offset, is deleted.
   */
  boolean isValid();

  /**
   * Returns current inlay's position in the document. This position is updated on document changes just like for a {@link RangeMarker}.
   */
  int getOffset();

  /**
   * Returns renderer, which defines size and representation for this inlay.
   */
  @NotNull
  EditorCustomElementRenderer getRenderer();

  /**
   * Returns current inlay's width. Width is defined at inlay's creation using information returned by inlay's renderer.
   * To change width, {@link #updateSize()} method should be called.
   */
  int getWidthInPixels();

  /**
   * Updates inlay's size by querying information from inlay's renderer.
   *
   * @see EditorCustomElementRenderer#calcWidthInPixels(Editor)
   */
  void updateSize();
}
