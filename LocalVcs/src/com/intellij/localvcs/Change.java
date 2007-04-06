package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public long getTimestamp() {
    throw new UnsupportedOperationException();
  }

  public abstract void applyTo(RootEntry r);

  public abstract void revertOn(RootEntry r);

  public abstract boolean affects(Entry e);

  public abstract boolean isCreationalFor(Entry e);

  public abstract List<Content> getContentsToPurge();
}
