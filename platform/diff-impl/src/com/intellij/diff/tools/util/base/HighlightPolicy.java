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

import com.intellij.diff.comparison.InnerFragmentsPolicy;
import org.jetbrains.annotations.NotNull;

public enum HighlightPolicy {
  BY_LINE("Highlight lines"),
  BY_WORD("Highlight words"),
  BY_WORD_SPLIT("Highlight split changes"),
  BY_CHAR("Highlight symbols"),
  DO_NOT_HIGHLIGHT("Do not highlight");

  @NotNull private final String myText;

  HighlightPolicy(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public boolean isShouldCompare() {
    return this != DO_NOT_HIGHLIGHT;
  }

  public boolean isFineFragments() {
    return getFragmentsPolicy() != InnerFragmentsPolicy.NONE;
  }

  public boolean isShouldSquash() {
    return this != BY_WORD_SPLIT;
  }

  @NotNull
  public InnerFragmentsPolicy getFragmentsPolicy() {
    switch (this) {
      case BY_WORD:
      case BY_WORD_SPLIT:
        return InnerFragmentsPolicy.WORDS;
      case BY_CHAR:
        return InnerFragmentsPolicy.CHARS;
      case BY_LINE:
      case DO_NOT_HIGHLIGHT:
        return InnerFragmentsPolicy.NONE;
      default:
        throw new IllegalArgumentException(this.name());
    }
  }
}
