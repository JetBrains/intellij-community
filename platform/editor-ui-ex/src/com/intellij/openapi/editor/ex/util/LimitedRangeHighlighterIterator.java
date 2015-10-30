/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;

/**
 * @author max
 */
public class LimitedRangeHighlighterIterator extends HighlighterIteratorWrapper {
  private final int myStartOffset;
  private final int myEndOffset;


  public LimitedRangeHighlighterIterator(final HighlighterIterator original, final int startOffset, final int endOffset) {
    super(original);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public int getStart() {
    return Math.max(super.getStart(), myStartOffset);
  }

  @Override
  public int getEnd() {
    return Math.min(super.getEnd(), myEndOffset);
  }

  @Override
  public boolean atEnd() {
    return super.atEnd() || super.getStart() >= myEndOffset || super.getEnd() <= myStartOffset;
  }

}
