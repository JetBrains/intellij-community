/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.fragmented;

import com.intellij.diff.util.Side;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

class HighlightRange {
  @NotNull private final TextRange myBase;
  @NotNull private final TextRange myChanged;
  @NotNull private final Side mySide;

  public HighlightRange(@NotNull Side side, @NotNull TextRange base, @NotNull TextRange changed) {
    mySide = side;
    myBase = base;
    myChanged = changed;
  }

  @NotNull
  public Side getSide() {
    return mySide;
  }

  @NotNull
  public TextRange getBase() {
    return myBase;
  }

  @NotNull
  public TextRange getChanged() {
    return myChanged;
  }
}
