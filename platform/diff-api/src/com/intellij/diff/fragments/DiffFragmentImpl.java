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

import com.intellij.openapi.diagnostic.Logger;

public class DiffFragmentImpl implements DiffFragment {
  private static final Logger LOG = Logger.getInstance(DiffFragmentImpl.class);

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

    if (myStartOffset1 == myEndOffset1 &&
        myStartOffset2 == myEndOffset2) {
      LOG.error("DiffFragmentImpl should not be empty: " + toString());
    }
    if (myStartOffset1 > myEndOffset1 ||
        myStartOffset2 > myEndOffset2) {
      LOG.error("DiffFragmentImpl is invalid: " + toString());
    }
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

  @Override
  public String toString() {
    return "DiffFragmentImpl [" + myStartOffset1 + ", " + myEndOffset1 + ") - [" + myStartOffset2 + ", " + myEndOffset2 + ")";
  }
}
