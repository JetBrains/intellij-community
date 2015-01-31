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
package com.intellij.diff.fragments;

public class DiffFragmentImpl implements DiffFragment {
  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  public DiffFragmentImpl(int startOffset1,
                          int endOffset1,
                          int startOffset2,
                          int endOffset2) {
    myStartOffset1 = startOffset1;
    myEndOffset1 = endOffset1;
    myStartOffset2 = startOffset2;
    myEndOffset2 = endOffset2;
  }

  public int getStartOffset1() {
    return myStartOffset1;
  }

  public int getEndOffset1() {
    return myEndOffset1;
  }

  public int getStartOffset2() {
    return myStartOffset2;
  }

  public int getEndOffset2() {
    return myEndOffset2;
  }
}
