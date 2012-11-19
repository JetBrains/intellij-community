package org.jetbrains.jps.javac;

import java.util.Arrays;

/**
* @author Eugene Zhuravlev
*         Date: 11/18/12
*/
public final class BinaryContent {
  private final byte[] myBuffer;
  private final int myOffset;
  private final int myLength;

  public BinaryContent(byte[] buf) {
    this(buf, 0, buf.length);
  }

  public BinaryContent(byte[] buf, int off, int len) {
    myBuffer = buf;
    myOffset = off;
    myLength = len;
  }

  public byte[] getBuffer() {
    return myBuffer;
  }

  public int getOffset() {
    return myOffset;
  }

  public int getLength() {
    return myLength;
  }

  public byte[] toByteArray() {
    return Arrays.copyOfRange(myBuffer, myOffset, myOffset + myLength);
  }
}
