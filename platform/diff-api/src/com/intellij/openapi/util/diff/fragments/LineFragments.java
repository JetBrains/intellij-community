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

import java.util.List;

public class LineFragments {
  @NotNull private final List<? extends LineFragment> myFragments;
  private final boolean myFine;

  public LineFragments(@NotNull List<? extends LineFragment> fragments, boolean isFine) {
    myFragments = fragments;
    myFine = isFine;
  }

  @NotNull
  public List<? extends LineFragment> getFragments() {
    return myFragments;
  }

  public List<? extends FineLineFragment> getFineFragments() {
    //noinspection unchecked
    return myFine ? (List<? extends FineLineFragment>)myFragments : null;
  }

  public boolean isFine() {
    return myFine;
  }

  //
  // Constructors
  //

  @NotNull
  public static LineFragments create(@NotNull List<? extends LineFragment> fragments) {
    return new LineFragments(fragments, false);
  }

  @NotNull
  public static LineFragments createFine(@NotNull List<? extends FineLineFragment> fragments) {
    return new LineFragments(fragments, true);
  }
}
