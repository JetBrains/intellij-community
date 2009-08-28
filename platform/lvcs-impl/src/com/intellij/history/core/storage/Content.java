package com.intellij.history.core.storage;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;

import java.io.IOException;

public abstract class Content {
  public void write(Stream s) throws IOException {
  }

  public abstract byte[] getBytes();

  public byte[] getBytesIfAvailable() {
    return isAvailable() ? getBytes() : null;
  }

  public String getString(Entry e, IdeaGateway gw) {
    return gw.stringFromBytes(getBytes(), e.getPath());
  }

  public abstract boolean isAvailable();

  public abstract void purge();

  @Override
  public String toString() {
    return new String(getBytes());
  }

  @Override
  public boolean equals(Object o) {
    return o != null && getClass().equals(o.getClass());
  }
}
