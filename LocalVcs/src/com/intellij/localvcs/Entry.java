package com.intellij.localvcs;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class Entry {
  // todo maybe make them private
  // todo make it basic type
  protected Integer myId;
  protected String myName;
  protected Long myTimestamp;
  protected DirectoryEntry myParent;

  public Entry(Integer id, String name, Long timestamp) {
    myId = id;
    myName = name;
    myTimestamp = timestamp;
  }

  public Entry(Stream s) throws IOException {
    myId = s.readInteger();
    myName = s.readString();
    myTimestamp = s.readLong();
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
    s.writeString(myName);
    s.writeLong(myTimestamp);
  }

  public Integer getId() {
    return myId;
  }

  public IdPath getIdPath() {
    if (myParent == null) return new IdPath(myId);
    return myParent.getIdPathAppendedWith(myId);
  }

  public String getName() {
    return myName;
  }

  public String getPath() {
    //todo try to remove this check
    if (myParent == null) return myName;
    return myParent.getPathAppendedWith(myName);
  }

  public Long getTimestamp() {
    return myTimestamp;
  }

  public boolean isOutdated(Long timestamp) {
    return !myTimestamp.equals(timestamp);
  }

  public String getContent() {
    throw new UnsupportedOperationException();
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
    throw new UnsupportedOperationException();
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException();
  }

  public List<Entry> getChildren() {
    return Collections.emptyList();
  }

  protected Entry findChild(Integer id) {
    for (Entry child : getChildren()) {
      if (child.getId().equals(id)) return child;
    }
    return null;
  }

  protected Entry findEntry(Matcher m) {
    if (m.matches(this)) return this;

    for (Entry child : getChildren()) {
      Entry result = child.findEntry(m);
      if (result != null) return result;
    }

    return null;
  }

  // todo try to get rid of entries copying
  public abstract Entry copy();

  public Entry renamed(String newName) {
    Entry result = copy();
    result.myName = newName;
    return result;
  }

  public Entry withContent(String newContent, Long timestamp) {
    throw new UnsupportedOperationException();
  }

  public abstract Difference getDifferenceWith(Entry e);

  protected abstract Difference asCreatedDifference();

  protected abstract Difference asDeletedDifference();

  protected interface Matcher {
    boolean matches(Entry entry);
  }
}
