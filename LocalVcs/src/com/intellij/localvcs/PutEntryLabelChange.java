package com.intellij.localvcs;

import java.io.IOException;

public class PutEntryLabelChange extends PutLabelChange {
  private String myPath; // transient
  private IdPath myAffectedIdPath;

  protected PutEntryLabelChange(String path, long timestamp, String name) {
    super(timestamp, name);
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

  public boolean affects(Entry e) {
    return e.getIdPath().startsWith(myAffectedIdPath);
  }
}