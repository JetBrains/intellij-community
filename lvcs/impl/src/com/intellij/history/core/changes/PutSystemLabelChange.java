package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class PutSystemLabelChange extends PutLabelChange {
  public PutSystemLabelChange(String name, long timestamp) {
    super(name, timestamp);
  }

  public PutSystemLabelChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  public boolean isSystemLabel() {
    return true;
  }
}