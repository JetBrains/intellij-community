// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface MarkupModelListener extends EventListener {
  MarkupModelListener[] EMPTY_ARRAY = new MarkupModelListener[0];

  default void afterAdded(@NotNull RangeHighlighterEx highlighter) {
  }

  default void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
  }

  /**
   * Called when the {@code highlighter} is disposed.
   * Inside this method the {@code highlighter.isValid()} returns {@code false}, so some methods might be unavailable or have undefined behaviour.
   * For example, all {@code setXXX} methods, e.g., {@link RangeHighlighterEx#setTextAttributes(TextAttributes)} might fail.
   * Only getters are guaranteed to work.
   */
  default void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
  }

  default void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
  }

  default void attributesChanged(@NotNull RangeHighlighterEx highlighter,
                                 boolean renderersChanged, boolean fontStyleChanged, boolean foregroundColorChanged) {
    attributesChanged(highlighter, renderersChanged, fontStyleChanged || foregroundColorChanged);
  }
}
