/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for the strategy that calculates the best place to apply wrap to particular line (sequence of characters).
 * <p/>
 * One possible use case for such functionality is a situation when user opens document with lines that are wider that editor's
 * visible area. We can represent such long strings in multiple visual line then and need to know the best place to insert
 * such virtual wrap.
 *
 * @author Denis Zhdanov
 * @since Aug 25, 2010 11:14:29 AM
 */
public interface LineWrapPositionStrategy {

  /**
   * Allows to calculate the most appropriate position to wrap target line.
   *
   * @param text                              target text holder
   * @param startOffset                       start offset to use with the given text holder (inclusive)
   * @param endOffset                         end offset to use with the given text holder (exclusive)
   * @param maxPreferredOffset                this method is expected to do its best to return offset that belongs to
   *                                          <code>(startOffset; maxPreferredOffset]</code> interval. However, it's allowed
   *                                          to return value from <code>(maxPreferredOffset; endOffset]</code> interval
   *                                          unless <code>'allowToBeyondMaxPreferredOffset'</code> if <code>'false'</code>
   * @param allowToBeyondMaxPreferredOffset   indicates if it's allowed to return value from
   *                                          <code>(maxPreferredOffset; endOffset]</code> interval in case of inability to
   *                                          find appropriate offset from <code>(startOffset; maxPreferredOffset]</code> interval
   * @return                                  offset from <code>(startOffset; endOffset]</code> interval where
   *                                          target line should be wrapped
   */
  int calculateWrapPosition(
    @NotNull CharSequence text, int startOffset, int endOffset, int maxPreferredOffset, boolean allowToBeyondMaxPreferredOffset
  );
}
