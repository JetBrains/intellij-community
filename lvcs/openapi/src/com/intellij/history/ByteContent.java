package com.intellij.history;

public class ByteContent {
  private final boolean myIsDirectory;
  private final byte[] myBytes;

  public ByteContent(boolean isDirectory, byte[] bytes) {
    myIsDirectory = isDirectory;
    myBytes = bytes;
  }

  public boolean isIsDirectory() {
    return myIsDirectory;
  }

  public byte[] getBytes() {
    return myBytes;
  }
}
