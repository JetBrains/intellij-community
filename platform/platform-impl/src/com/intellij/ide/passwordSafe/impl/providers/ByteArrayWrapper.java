/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.passwordSafe.impl.providers;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The wrapper for byte arrays that allows to use it as a key in maps
 */
public class ByteArrayWrapper implements Comparable<ByteArrayWrapper> {
  /**
   * The data
   */
  private final byte[] myData;

  /**
   * The constructor
   * @param data the data element
   */
  public ByteArrayWrapper(@NotNull byte[] data) {
    myData = data;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Arrays.hashCode(myData);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    return obj instanceof ByteArrayWrapper && Arrays.equals(myData, ((ByteArrayWrapper)obj).myData);
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(ByteArrayWrapper o) {
    if(o == null) return -1;
    int n = Math.max(o.myData.length, myData.length);
    for(int i = 0; i<n; i++) {
      int d = myData[i] - o.myData[i];
      if(d != 0) {
        return d;
      }
    }
    return myData.length - o.myData.length;
  }

  /**
   * @return the wrapped array
   */
  public byte[] unwrap() {
    return myData;
  }
}
