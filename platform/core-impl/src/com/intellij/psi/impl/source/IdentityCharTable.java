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

package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/*
 * User: max
 * Date: Sep 16, 2006
 */
public class IdentityCharTable implements CharTable {
  private IdentityCharTable() { }

  public static final IdentityCharTable INSTANCE = new IdentityCharTable();

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence text) {
    return text;
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset) {
    if (startOffset == 0  && endOffset == baseText.length()) return baseText;
    return baseText.subSequence(startOffset, endOffset);
  }
}
