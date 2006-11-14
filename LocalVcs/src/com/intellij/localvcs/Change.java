package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public abstract void applyTo(RootEntry root);

  public abstract void revertOn(RootEntry root);

  public boolean affects(Entry e) {
    // todo test it
    for (IdPath p : getAffectedEntryIdPaths()) {
      if (p.contains(e.getId())) return true;
    }
    return false;
  }

  protected abstract List<IdPath> getAffectedEntryIdPaths();

  public abstract List<Difference> getDifferences(RootEntry r, Entry e);
}
