package com.intellij.history.core.storage;

import com.intellij.openapi.vfs.CharsetToolkit;

import java.io.IOException;

public abstract class Content {
  public void write(Stream s) throws IOException {
  }

  public abstract byte[] getBytes();

  public String getString() {
    return CharsetToolkit.bytesToString(getBytes());
  }

  public abstract boolean isAvailable();

  public abstract void purge();

  @Override
  public String toString() {
    return getString();
  }

  @Override
  public boolean equals(Object o) {
    return o != null && getClass().equals(o.getClass());
  }
}
