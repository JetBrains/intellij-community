// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.event;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface MarkupModelListener extends EventListener {
  MarkupModelListener[] EMPTY_ARRAY = new MarkupModelListener[0];

  default void afterAdded(@NotNull RangeHighlighterEx highlighter) {
  }

  default void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
  }

  default void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
  }

  /**
   * @deprecated Use {@link MarkupModelListener} directly.
   */
  @Deprecated
  abstract class Adapter implements MarkupModelListener {
  }
}
