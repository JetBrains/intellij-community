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
package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.event.DocumentListener;

import java.util.Comparator;

/**
 * @author max
 */
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
      return getPriority(o1) - getPriority(o2);
    }

    private int getPriority(Object o) {
      assert o != null;
      if (o instanceof PrioritizedDocumentListener) return ((PrioritizedDocumentListener)o).getPriority();
      return Integer.MAX_VALUE;
    }
  };

  int getPriority();
}
