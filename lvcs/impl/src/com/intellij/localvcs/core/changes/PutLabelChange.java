package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PutLabelChange extends Change {
  private String myName;
  private long myTimestamp;
  private boolean myIsMark;

  public PutLabelChange(String name, long timestamp, boolean isMark) {
    myTimestamp = timestamp;
    myName = name;
    myIsMark = isMark;
  }

  public PutLabelChange(Stream s) throws IOException {
    myTimestamp = s.readLong();
    myName = s.readString();
    myIsMark = s.readBoolean();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeLong(myTimestamp);
    s.writeString(myName);
    s.writeBoolean(myIsMark);
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
  public boolean isMark() {
    return myIsMark;
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
