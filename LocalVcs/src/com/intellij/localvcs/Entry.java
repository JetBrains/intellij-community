package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

public abstract class Entry {
  protected Integer myId;  // todo maybe make it private and make it basic type
  protected Long myTimestamp;
  private DirectoryEntry myParent;

  public Entry(Integer id, Long timestamp) {
    myId = id;
    myTimestamp = timestamp;
  }

  public Entry(Stream s) throws IOException {
    myId = s.readInteger();
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
  }

  // todo generalize Path and IdPath
  public Integer getId() {
    return myId;
  }

  public IdPath getIdPath() {
    if (myParent == null) return new IdPath(myId);
    return myParent.getIdPathAppendedWith(myId);
  }

  public abstract String getName();

  public Path getPath() {
    //todo try to remove this check
    if (!hasParent()) return new Path(getName());
    return myParent.getPathAppendedWith(getName());
  }

  public Long getTimestamp() {
    return myTimestamp;
  }

  public void setTimestamp(Long timestamp) {
    myTimestamp = timestamp;
  }

  public boolean isOutdated(Long timestamp) {
    return !myTimestamp.equals(timestamp);
  }

  public String getContent() {
    throw new UnsupportedOperationException();
  }

  private boolean hasParent() {
    return myParent != null;
  }

  public DirectoryEntry getParent() {
    return myParent;
  }

  protected void setParent(DirectoryEntry parent) {
    myParent = parent;
  }

  public boolean isDirectory() {
    return false;
  }

  public void addChild(Entry child) {
    throw new LocalVcsException();
  }

  public void removeChild(Entry child) {
    throw new LocalVcsException();
  }

  public List<Entry> getChildren() {
    throw new LocalVcsException();
  }

  protected Entry getChild(Integer id) {
    throw new LocalVcsException();
  }

  protected Entry findEntry(Matcher m) {
    return m.matches(this) ? this : null;
  }

  public abstract Entry copy();

  public abstract Entry renamed(String newName, Long timestamp);

  public Entry withContent(String newContent, Long timestamp) {
    throw new LocalVcsException();
  }

  public abstract Difference getDifferenceWith(Entry e);

  protected abstract Difference asCreatedDifference();

  protected abstract Difference asDeletedDifference();

  protected interface Matcher {
    boolean matches(Entry entry);
  }
}
