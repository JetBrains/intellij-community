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

package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.util.TextRange;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Nov 26, 2008
 * Time: 6:26:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class AffectedTextRange extends TextRange {
  private final boolean myProcessHeadingWhiteSpace;

  public AffectedTextRange(final int startOffset, final int endOffset, final boolean processHeadingWhiteSpace) {
    super(startOffset, endOffset);
    myProcessHeadingWhiteSpace = processHeadingWhiteSpace;
  }

  public AffectedTextRange(final TextRange affectedRange, final boolean processHeadingWhitespace) {
    this(affectedRange.getStartOffset(), affectedRange.getEndOffset(), processHeadingWhitespace);
  }

  public boolean isProcessHeadingWhiteSpace() {
    return myProcessHeadingWhiteSpace;
  }
}
