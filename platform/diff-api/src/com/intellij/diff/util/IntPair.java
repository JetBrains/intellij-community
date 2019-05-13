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

public class IntPair {
  public final int val1;
  public final int val2;

  public IntPair(int val1, int val2) {
    this.val1 = val1;
    this.val2 = val2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    IntPair pair = (IntPair)o;

    if (val1 != pair.val1) return false;
    if (val2 != pair.val2) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = val1;
    result = 31 * result + val2;
    return result;
  }

  @Override
  public String toString() {
    return "{" + val1 + ", " + val2 + "}";
  }
}
