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
package com.intellij.openapi.util.diff.fragments;

import org.jetbrains.annotations.NotNull;

public class LineFragmentImpl implements LineFragment {
  protected final int myStartLine1;
  protected final int myEndLine1;
  protected final int myStartLine2;
  protected final int myEndLine2;

  protected final int myStartOffset1;
  protected final int myEndOffset1;
  protected final int myStartOffset2;
  protected final int myEndOffset2;

  public LineFragmentImpl(@NotNull LineFragment fragment) {
    this(
      fragment.getStartLine1(),
      fragment.getEndLine1(),
      fragment.getStartLine2(),
      fragment.getEndLine2(),
      fragment.getStartOffset1(),
      fragment.getEndOffset1(),
      fragment.getStartOffset2(),
      fragment.getEndOffset2()
    );
  }

  public LineFragmentImpl(int startLine1, int endLine1, int startLine2, int endLine2,
                          int startOffset1, int endOffset1, int startOffset2, int endOffset2) {
    myStartLine1 = startLine1;
    myEndLine1 = endLine1;
    myStartLine2 = startLine2;
    myEndLine2 = endLine2;
    myStartOffset1 = startOffset1;
    myEndOffset1 = endOffset1;
    myStartOffset2 = startOffset2;
    myEndOffset2 = endOffset2;
  }

  @Override
  public int getStartLine1() {
    return myStartLine1;
  }

  @Override
  public int getEndLine1() {
    return myEndLine1;
  }

  @Override
  public int getStartLine2() {
    return myStartLine2;
  }

  @Override
  public int getEndLine2() {
    return myEndLine2;
  }

  @Override
  public int getStartOffset1() {
    return myStartOffset1;
  }

  @Override
  public int getEndOffset1() {
    return myEndOffset1;
  }

  @Override
  public int getStartOffset2() {
    return myStartOffset2;
  }

  @Override
  public int getEndOffset2() {
    return myEndOffset2;
  }
}
