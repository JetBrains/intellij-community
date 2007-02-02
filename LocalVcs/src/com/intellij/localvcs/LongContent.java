package com.intellij.localvcs;

import java.io.IOException;

public class LongContent extends Content {
  public static final int MAX_LENGTH = 100 * 1024;

  public LongContent() {
    super(null, -1);
  }

  public LongContent(Stream s) throws IOException {
    this();
  }

  @Override
  public void write(Stream s) throws IOException {
  }

  @Override
  public byte[] getBytes() {
    return "content is too long".getBytes();
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public boolean isTooLong() {
    return true;
  }
}
