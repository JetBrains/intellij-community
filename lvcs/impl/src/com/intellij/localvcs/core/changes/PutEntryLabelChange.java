package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;

public class PutEntryLabelChange extends PutLabelChange {
  private String myPath; // transient
  private IdPath myAffectedIdPath;

  public PutEntryLabelChange(long timestamp, String path, String name, boolean isSystemMark) {
    super(timestamp, name, isSystemMark);
    myPath = path;
  }

  public PutEntryLabelChange(Stream s) throws IOException {
    super(s);
    myAffectedIdPath = s.readIdPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeIdPath(myAffectedIdPath);
  }

  @Override
  public void applyTo(RootEntry r) {
    myAffectedIdPath = r.getEntry(myPath).getIdPath();
  }

  @Override
  public boolean affects(IdPath... pp) {
    for (IdPath p : pp) {
      if (p.startsWith(myAffectedIdPath)) return true;
    }
    return false;
  }
}