package com.intellij.localvcs;

import java.io.IOException;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public abstract void applyTo(Snapshot snapshot);

  public abstract void revertOn(Snapshot snapshot);
}
