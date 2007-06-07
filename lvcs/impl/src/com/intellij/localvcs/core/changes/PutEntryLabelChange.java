package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;

public class PutEntryLabelChange extends PutLabelChange {
  private String myPath; // transient
  private IdPath myAffectedIdPath;

  public PutEntryLabelChange(String path, String name, long timestamp, boolean isMark) {
    super(name, timestamp, isMark);
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
  public void applyTo(Entry r) {
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