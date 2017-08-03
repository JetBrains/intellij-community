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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EventListener;
import java.util.List;

/**
 * Provides an ability to introduce custom visual elements into editor's representation.
 * Such elements are not reflected in document contents.
 * <p>
 * WARNING! This is an experimental API, it can change at any time.
 */
@ApiStatus.Experimental
public interface InlayModel {
  /**
   * Introduces an inline visual element at a given offset, its width and appearance is defined by the provided renderer. With respect to
   * document changes, created element behaves in a similar way to a zero-range {@link RangeMarker}. This method returns {@code null}
   * if requested element cannot be created, e.g. if corresponding functionality is not supported by current editor instance.
   */
  @Nullable
  Inlay addInlineElement(int offset, @NotNull EditorCustomElementRenderer renderer);

  /**
   * Returns a list of inline elements for a given offset range (both limits are inclusive). Returned list is sorted by offset.
   */
  @NotNull
  List<Inlay> getInlineElementsInRange(int startOffset, int endOffset);

  /**
   * Tells whether there exists an inline visual element at a given offset.
   */
  boolean hasInlineElementAt(int offset);

  /**
   * Tells whether there exists an inline visual element at a given visual position.
   * Only visual position to the left of the element is recognized.
   */
  boolean hasInlineElementAt(@NotNull VisualPosition visualPosition);

  /**
   * Return a custom visual element at given coordinates in editor's coordinate space,
   * or {@code null} if there's no element at given point.
   */
  @Nullable
  Inlay getElementAt(@NotNull Point point);

  /**
   * Adds a listener that will be notified after adding, updating and removal of custom visual elements.
   */
  void addListener(@NotNull Listener listener, @NotNull Disposable disposable);

  interface Listener extends EventListener {
    void onAdded(@NotNull Inlay inlay);

    void onUpdated(@NotNull Inlay inlay);

    void onRemoved(@NotNull Inlay inlay);
  }

  /**
   * An adapter useful for the cases, when the same action is to be performed after custom visual element's adding, updating and removal.
   */
  abstract class SimpleAdapter implements Listener {
    @Override
    public void onAdded(@NotNull Inlay inlay) {
      onUpdated(inlay);
    }

    @Override
    public void onUpdated(@NotNull Inlay inlay) {}

    @Override
    public void onRemoved(@NotNull Inlay inlay) {
      onUpdated(inlay);
    }
  }
}
