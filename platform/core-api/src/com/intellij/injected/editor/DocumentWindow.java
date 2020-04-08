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

/*
 * @author max
 */
package com.intellij.injected.editor;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DocumentWindow extends Document {
  @NotNull
  Document getDelegate();

  /**
   * @deprecated use {@link #injectedToHost(int)} instead
   */
  @Deprecated
  default int hostToInjectedUnescaped(int hostOffset) {
    return injectedToHost(hostOffset);
  }

  int injectedToHost(int injectedOffset);

  /**
   * @param minHostOffset if {@code true} minimum host offset corresponding to given injected offset is returned, otherwise maximum related
   *                      host offset is returned
   */
  int injectedToHost(int injectedOffset, boolean minHostOffset);

  @NotNull
  TextRange injectedToHost(@NotNull TextRange injectedOffset);

  int hostToInjected(int hostOffset);

  @Nullable
  TextRange getHostRange(int hostOffset);

  int injectedToHostLine(int line);

  Segment @NotNull [] getHostRanges();

  boolean areRangesEqual(@NotNull DocumentWindow documentWindow);

  boolean isValid();

  boolean containsRange(int hostStart, int hostEnd);

  boolean isOneLine();
}
