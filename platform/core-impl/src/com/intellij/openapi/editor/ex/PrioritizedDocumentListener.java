// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.event.DocumentListener;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

@FunctionalInterface
public interface PrioritizedDocumentListener extends DocumentListener {
  /**
   * Comparator that sorts {@link DocumentListener} objects by their {@link PrioritizedDocumentListener#getPriority() priorities} (if any).
   * <p/>
   * The rules are:
   * <pre>
   * <ul>
   *   <li>{@link PrioritizedDocumentListener} has more priority than {@link DocumentListener};</li>
   *   <li>{@link PrioritizedDocumentListener} with lower value returned from {@link #getPriority()} has more priority than another;</li>
   * </ul>
   * </pre>
   */
  Comparator<? super DocumentListener> COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      return Integer.compare(getPriority(o1), getPriority(o2));
    }

    private int getPriority(@NotNull Object o) {
      if (o instanceof PrioritizedDocumentListener) {
        return ((PrioritizedDocumentListener)o).getPriority();
      }
      return Integer.MAX_VALUE;
    }
  };

  int getPriority();
}
