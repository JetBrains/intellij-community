package com.intellij.localvcs;

import java.util.Arrays;

public class ByteContent extends Content {
  private byte[] myData;

  public ByteContent(byte[] bytes) {
    super(null, 0);
    myData = bytes;
  }

  @Override
  public byte[] getData() {
    return myData;
  }

  @Override
  public boolean equals(Object o) {
    return Arrays.equals(myData, ((Content)o).getData());
  }

  @Override
  public int hashCode() {
    return myData.hashCode();
  }
}
