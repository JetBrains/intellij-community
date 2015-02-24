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
package com.intellij.diff.tools.util.base;

import com.intellij.diff.comparison.ComparisonPolicy;
import org.jetbrains.annotations.NotNull;

public enum IgnorePolicy {
  DEFAULT("Do not ignore"),
  TRIM_WHITESPACES("Trim whitespaces"),
  IGNORE_WHITESPACES("Ignore whitespaces"),
  IGNORE_WHITESPACES_CHUNKS("Ignore whitespaces and empty lines");

  @NotNull private final String myText;

  IgnorePolicy(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public ComparisonPolicy getComparisonPolicy() {
    switch (this) {
      case DEFAULT:
        return ComparisonPolicy.DEFAULT;
      case TRIM_WHITESPACES:
        return ComparisonPolicy.TRIM_WHITESPACES;
      case IGNORE_WHITESPACES:
        return ComparisonPolicy.IGNORE_WHITESPACES;
      case IGNORE_WHITESPACES_CHUNKS:
        return ComparisonPolicy.IGNORE_WHITESPACES;
      default:
        throw new IllegalArgumentException(this.name());
    }
  }

  public boolean isShouldTrimChunks() {
    return this == IGNORE_WHITESPACES_CHUNKS;
  }
}