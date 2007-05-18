package com.intellij.localvcs.core.tree;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;

import java.io.IOException;
import static java.lang.String.format;
import java.util.Collections;
import java.util.List;

public abstract class Entry {
  // todo make them basic type (quite possible)
  protected int myId;
  protected String myName;
  protected DirectoryEntry myParent;

  public Entry(int id, String name) {
    myId = id;
    myName = name;
  }

  public Entry(Stream s) throws IOException {
    myId = s.readInteger();
    myName = s.readString();
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
    s.writeString(myName);
  }

  public int getId() {
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
    if (myParent == null) return myName;
    return myParent.getPathAppendedWith(myName);
  }

  public boolean nameEquals(String name) {
    return Paths.equals(myName, name);
  }

  public boolean pathEquals(String path) {
    return Paths.equals(getPath(), path);
  }

  public long getTimestamp() {
    throw new UnsupportedOperationException();
  }

  public boolean isOutdated(long timestamp) {
    return getTimestamp() != timestamp;
  }

  public Content getContent() {
    throw new UnsupportedOperationException();
  }

  public boolean hasUnavailableContent() {
    return false;
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

  public Entry findChild(String name) {
    for (Entry e : getChildren()) {
      if (e.nameEquals(name)) return e;
    }
    return null;
  }

  public boolean hasEntry(String path) {
    return findEntry(path) != null;
  }

  public Entry getEntry(String path) {
    Entry result = findEntry(path);
    if (result == null) {
      throw new RuntimeException(format("entry '%s' not found", path));
    }
    return result;
  }

  public Entry findEntry(String path) {
    String withoutMe = Paths.withoutRootIfUnder(path, myName);

    if (withoutMe == null) return null;
    if (withoutMe.length() == 0) return this;

    return searchInChildren(withoutMe);
  }

  protected Entry searchInChildren(String path) {
    for (Entry e : getChildren()) {
      Entry result = e.findEntry(path);
      if (result != null) return result;
    }
    return null;
  }

  // todo generalize findEntry(*) methods
  public Entry findEntry(IdPath p) {
    if (!p.rootEquals(myId)) return null;
    if (p.getId() == myId) return this;
    return searchInChildren(p.withoutRoot());
  }

  protected Entry searchInChildren(IdPath p) {
    for (Entry e : getChildren()) {
      Entry result = e.findEntry(p);
      if (result != null) return result;
    }
    return null;
  }

  public Entry getEntry(IdPath p) {
    Entry result = findEntry(p);
    if (result == null) {
      throw new RuntimeException(format("entry '%s' not found", p.toString()));
    }
    return result;
  }

  public Entry getEntry(int id) {
    // todo it's very slow
    // todo get rid of this method
    Entry result = findEntry(id);
    if (result == null) {
      throw new RuntimeException(format("entry #%d not found", id));
    }
    return result;
  }

  private Entry findEntry(int id) {
    if (id == myId) return this;

    for (Entry child : getChildren()) {
      Entry result = child.findEntry(id);
      if (result != null) return result;
    }

    return null;
  }

  // todo try to get rid of entries copying
  public abstract Entry copy();

  public void changeName(String newName) {
    if (myParent != null) myParent.checkDoesNotExist(this, newName);
    myName = newName;
  }

  public void changeContent(Content newContent, long timestamp) {
    throw new UnsupportedOperationException();
  }

  public abstract Difference getDifferenceWith(Entry e);

  protected abstract Difference asCreatedDifference();

  protected abstract Difference asDeletedDifference();

  @Override
  public String toString() {
    return String.valueOf(myId) + "-" + myName;
  }
}
