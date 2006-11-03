package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

public abstract class Entry {
  protected Integer myObjectId;
  protected String myName;
  protected DirectoryEntry myParent;

  public Entry(Integer objectId, String name) {
    myObjectId = objectId;
    myName = name;
  }

  public Entry(Stream s) throws IOException {
    myObjectId = s.readNullableInteger();
    myName = s.readNullableString();
  }

  public void write(Stream s) throws IOException {
    s.writeNullableInteger(myObjectId);
    s.writeNullableString(myName);
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public String getName() {
    return myName;
  }

  public Path getPath() {
    // todo try to remove this check
    if (!hasParent()) return new Path(myName);
    return myParent.getPathAppendedWith(myName);
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

  public Boolean isDirectory() {
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

  protected Entry findEntry(Matcher m) {
    return m.matches(this) ? this : null;
  }

  public abstract Entry copy();

  public Entry renamed(String newName) {
    Entry result = copy();
    result.myName = newName;
    return result;
  }

  protected interface Matcher {
    boolean matches(Entry entry);
  }
}
