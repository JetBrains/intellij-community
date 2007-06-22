package com.intellij.history.core.storage;

public class UnavailableContent extends Content {
  @Override
  public byte[] getBytes() {
    throw new RuntimeException();
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public void purge() {
  }
}
