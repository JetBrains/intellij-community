package com.intellij.localvcs;

import java.io.IOException;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public abstract void applyTo(RootEntry root);

  public abstract void revertOn(RootEntry root);

  public boolean affects(Entry entry) {
    return entry.getIdPath().isPrefixOf(getAffectedEntryIdPath());
  }

  protected abstract IdPath getAffectedEntryIdPath();
}
