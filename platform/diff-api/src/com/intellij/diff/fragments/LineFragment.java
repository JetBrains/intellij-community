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

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Modified part of the text
 *
 * Offset ranges cover whole line, including '\n' at the end. But '\n' can be absent for the last line.
 */
public interface LineFragment extends DiffFragment {
  int getStartLine1();

  int getEndLine1();

  int getStartLine2();

  int getEndLine2();

  /**
   * High-granularity changes inside line fragment (ex: detected by ByWord)
   * Offsets of inner changes are relative to the start of LineFragment.
   *
   * null - no inner similarities was found
   */
  @Nullable
  List<DiffFragment> getInnerFragments();
}
