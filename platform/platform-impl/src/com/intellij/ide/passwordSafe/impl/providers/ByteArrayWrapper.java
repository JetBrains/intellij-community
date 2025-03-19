// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe.impl.providers;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The wrapper for byte arrays that allows to use it as a key in maps
 */
@ApiStatus.Internal
public final class ByteArrayWrapper implements Comparable<ByteArrayWrapper> {
  /**
   * The data
   */
  private final byte[] myData;

  /**
   * The constructor
   * @param data the data element
   */
  public ByteArrayWrapper(byte @NotNull [] data) {
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
  @Override
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
