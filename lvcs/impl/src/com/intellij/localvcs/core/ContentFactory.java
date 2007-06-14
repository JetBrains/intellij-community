package com.intellij.localvcs.core;

import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Storage;
import com.intellij.localvcs.core.storage.UnavailableContent;

import java.io.IOException;
import java.util.Arrays;


public abstract class ContentFactory {
  public static final int MAX_CONTENT_LENGTH = 1024 * 1024;

  public Content createContent(Storage s) {
    try {
      if (isTooLong()) return new UnavailableContent();
      return s.storeContent(getBytes());
    }
    catch (IOException e) {
      return new UnavailableContent();
    }
  }

  private boolean isTooLong() throws IOException {
    return getLength() > MAX_CONTENT_LENGTH;
  }

  public boolean equalsTo(Content c) {
    try {
      if (!c.isAvailable()) return false;
      if (isTooLong()) return false;

      if (getLength() != c.getBytes().length) return false;
      return Arrays.equals(getBytes(), c.getBytes());
    }
    catch (IOException e) {
      return false;
    }
  }

  protected abstract byte[] getBytes() throws IOException;

  protected abstract long getLength() throws IOException;
}
