package com.intellij.history.core.storage;

import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;

public abstract class Content {
  private static boolean ourUseCharsetRecognition = true;

  @TestOnly
  public static void useCharsetRecognition(boolean use) {
    ourUseCharsetRecognition = use;
  }

  public void write(Stream s) throws IOException {
  }

  public abstract byte[] getBytes();

  public byte[] getBytesIfAvailable() {
    return isAvailable() ? getBytes() : null;
  }

  public String getString() {
    if (!ourUseCharsetRecognition) return new String(getBytes());
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
