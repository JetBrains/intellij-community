/*
 * @author max
 */
package com.intellij.psi.stubs;

import java.util.Arrays;

public class SerializedStubContent {
  private final byte[] myBytes;

  public SerializedStubContent(final byte[] bytes) {
    myBytes = bytes;
  }

  public byte[] getBytes() {
    return myBytes;
  }

  public boolean equals(final Object that) {
    return this == that ||
           that instanceof SerializedStubContent && Arrays.equals(myBytes, ((SerializedStubContent)that).myBytes);
  }

  public int hashCode() {
    return Arrays.hashCode(myBytes);
  }
}