package com.intellij.history.core.tree;

import com.intellij.history.core.storage.Stream;

import java.io.IOException;

// todo replace all String.length() == 0 with String.isEmpty()
public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super(-1, "");
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  protected String getPathAppendedWith(String name) {
    return name;
  }

  @Override
  public Entry findEntry(String path) {
    return searchInChildren(path);
  }

  @Override
  protected DirectoryEntry copyEntry() {
    return new RootEntry();
  }
}
