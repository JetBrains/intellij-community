package com.intellij.localvcs.core.storage;

import java.util.Arrays;

public class ByteContent extends Content {
  private byte[] myData;

  public ByteContent(byte[] bytes) {
    super(null, 0);
    myData = bytes;
  }

  @Override
  public byte[] getBytes() {
    return myData;
  }

  @Override
  public boolean equals(Object o) {
    if (o.getClass().equals(UnavailableContent.class)) return false;
    return Arrays.equals(myData, ((Content)o).getBytes());
  }

  @Override
  public int hashCode() {
    return myData.hashCode();
  }
}
