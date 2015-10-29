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
package com.intellij.diff.util;

public class MergeRange {
  public final int start1;
  public final int end1;
  public final int start2;
  public final int end2;
  public final int start3;
  public final int end3;

  public MergeRange(int start1, int end1, int start2, int end2, int start3, int end3) {
    this.start1 = start1;
    this.end1 = end1;
    this.start2 = start2;
    this.end2 = end2;
    this.start3 = start3;
    this.end3 = end3;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MergeRange range = (MergeRange)o;

    if (start1 != range.start1) return false;
    if (end1 != range.end1) return false;
    if (start2 != range.start2) return false;
    if (end2 != range.end2) return false;
    if (start3 != range.start3) return false;
    if (end3 != range.end3) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = start1;
    result = 31 * result + end1;
    result = 31 * result + start2;
    result = 31 * result + end2;
    result = 31 * result + start3;
    result = 31 * result + end3;
    return result;
  }

  @Override
  public String toString() {
    return "[" + start1 + ", " + end1 + ") - [" + start2 + ", " + end2 + ") - [" + start3 + ", " + end3 + ")";
  }

  public boolean isEmpty() {
    return start1 == end1 && start2 == end2 && start3 == end3;
  }
}
