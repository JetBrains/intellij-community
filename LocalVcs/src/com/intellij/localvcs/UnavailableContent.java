package com.intellij.localvcs;

import java.io.IOException;

public class UnavailableContent extends Content {
  public UnavailableContent() {
    super(null, -1);
  }

  public UnavailableContent(Stream s) throws IOException {
    this();
  }

  @Override
  public void write(Stream s) throws IOException {
  }

  @Override
  public byte[] getBytes() {
    return "content is not available".getBytes();
  }

  @Override
  public int getLength() {
    return 0;
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public void purge() {
  }
}
