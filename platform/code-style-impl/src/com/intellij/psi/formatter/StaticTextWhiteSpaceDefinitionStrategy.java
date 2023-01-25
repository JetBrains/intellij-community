/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.formatter;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StaticTextWhiteSpaceDefinitionStrategy extends AbstractWhiteSpaceFormattingStrategy {
  private final Set<CharSequence> myWhiteSpaces = new HashSet<>();

  public StaticTextWhiteSpaceDefinitionStrategy(CharSequence @NotNull ... whiteSpaces) {
    myWhiteSpaces.addAll(Arrays.asList(whiteSpaces));
  }

  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    for (CharSequence whiteSpace : myWhiteSpaces) {
      if (end - start >= whiteSpace.length() && Strings.startsWith(text, start, whiteSpace)) {
        return start + whiteSpace.length();
      }
    }

    return start;
  }
}
