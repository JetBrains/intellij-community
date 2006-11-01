package com.intellij.localvcs;

public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super(null, null);
  }

  protected Path getPathAppendedWith(String name) {
    return new Path(name);
  }
}
