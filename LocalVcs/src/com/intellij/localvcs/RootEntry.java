package com.intellij.localvcs;

public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super(-1, "");
  }

  protected Path getPathAppendedWith(String name) {
    return new Path(name);
  }
}
