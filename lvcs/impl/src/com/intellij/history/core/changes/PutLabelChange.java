package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PutLabelChange extends Change {
  private String myName;
  private long myTimestamp;

  public PutLabelChange(String name, long timestamp) {
    myName = name;
    myTimestamp = timestamp;
  }

  public PutLabelChange(Stream s) throws IOException {
    myName = s.readString();
    myTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myName);
    s.writeLong(myTimestamp);
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public void applyTo(Entry r) {
  }

  @Override
  public void revertOn(Entry r) {
  }

  @Override
  public boolean affects(IdPath... pp) {
    return true;
  }

  @Override
  public boolean affectsOnlyInside(Entry e) {
    throw new UnsupportedOperationException();
  }

  public boolean isCreationalFor(Entry e) {
    return false;
  }

  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }

  @Override
  public boolean isLabel() {
    return true;
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
