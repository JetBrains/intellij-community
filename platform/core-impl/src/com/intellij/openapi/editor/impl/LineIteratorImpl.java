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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.LineIterator;

public class LineIteratorImpl implements LineIterator {
  private int myLineIndex = 0;
  private final LineSet myLineSet;

  LineIteratorImpl(LineSet lineSet) {
    myLineSet = lineSet;
  }

  @Override
  public void start(int startOffset) {
    myLineIndex = myLineSet.findLineIndex(startOffset);
  }

  @Override
  public int getStart() {
    return myLineSet.getLineStart(myLineIndex);
  }

  @Override
  public int getEnd() {
    return myLineSet.getLineEnd(myLineIndex);
  }

  @Override
  public int getSeparatorLength() {
    return myLineSet.getSeparatorLength(myLineIndex);
  }

  @Override
  public int getLineNumber() {
    return myLineIndex;
  }

  @Override
  public void advance() {
    myLineIndex++;
  }

  @Override
  public boolean atEnd() {
    return myLineIndex >= myLineSet.getLineCount() || myLineIndex < 0;
  }


}
