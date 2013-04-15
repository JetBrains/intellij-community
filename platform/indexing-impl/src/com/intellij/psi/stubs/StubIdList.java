/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.util.IncorrectOperationException;

// List of nonnegative ints, monotonically increasing, optimized for one int case (90% of our lists one element)
public final class StubIdList {
  private final int myData;
  private final int[] myArray;

  public StubIdList() {
    myData = -1;
    myArray = null;
  }

  public StubIdList(int value) {
    assert value >= 0;
    myData = value;
    myArray = null;
  }

  public StubIdList(int[] array, int size) {
    myArray = array;
    myData = size;
  }

  @Override
  public int hashCode() {
    if (myArray == null) return myData;
    int value = 0;
    for(int i = 0; i < myData; ++i) {
      value = value * 37 + myArray[i];
    }
    return value;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof StubIdList)) return false;
    StubIdList other = (StubIdList)obj;
    if (myArray == null) {
      return other.myArray == null && other.myData == myData;
    } else {
      if (other.myArray == null || myData != other.myData) return false;
      for(int i = 0; i < myData; ++i) {
        if (myArray[i] != other.myArray[i]) return false;
      }
      return true;
    }
  }

  public int size() {
    return myArray == null ? myData >= 0 ? 1 : 0: myData;
  }

  public int get(int i) {
    if (myArray == null) {
      assert myData >= 0;
      if (i == 0) return myData;
      throw new IncorrectOperationException();
    } else {
      if (i >= myData) throw new IncorrectOperationException();
      return myArray[i];
    }
  }
}
