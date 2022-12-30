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
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum HighlightPolicy {
  BY_LINE("option.highlighting.policy.lines"),
  BY_WORD("option.highlighting.policy.words"),
  BY_WORD_SPLIT("option.highlighting.policy.split"),
  BY_CHAR("option.highlighting.policy.symbols"),
  DO_NOT_HIGHLIGHT("option.highlighting.policy.none");

  @NotNull private final String myTextKey;

  HighlightPolicy(@NotNull @PropertyKey(resourceBundle = DiffBundle.BUNDLE) String textKey) {
    myTextKey = textKey;
  }

  @Nls
  @NotNull
  public String getText() {
    return DiffBundle.message(myTextKey);
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
    return switch (this) {
      case BY_WORD, BY_WORD_SPLIT -> InnerFragmentsPolicy.WORDS;
      case BY_CHAR -> InnerFragmentsPolicy.CHARS;
      case BY_LINE, DO_NOT_HIGHLIGHT -> InnerFragmentsPolicy.NONE;
    };
  }
}
