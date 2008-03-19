/*
 * @author max
 */
package com.intellij.psi.stubs;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;

public class SerializedStubTree {
  private final byte[] myBytes;

  public SerializedStubTree(final byte[] bytes) {
    myBytes = bytes;
  }

  public StubElement getStub() {
    return SerializationManager.getInstance().deserialize(new DataInputStream(new ByteArrayInputStream(myBytes)));
  }

  public boolean equals(final Object that) {
    return this == that ||
           that instanceof SerializedStubTree && Arrays.equals(myBytes, ((SerializedStubTree)that).myBytes);
  }

  public int hashCode() {
    return Arrays.hashCode(myBytes);
  }

  public byte[] getBytes() {
    return myBytes;
  }
}