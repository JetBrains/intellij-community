package com.intellij.history.core.tree;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;

import java.io.IOException;
import static java.lang.String.format;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public abstract class Entry {
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
    return myParent.getIdPath().appendedWith(myId);
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
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean isReadOnly() {
    throw new UnsupportedOperationException(formatPath());
  }

  public void setReadOnly(boolean isReadOnly) {
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean isOutdated(long timestamp) {
    return getTimestamp() != timestamp;
  }

  public Content getContent() {
    throw new UnsupportedOperationException(formatPath());
  }

  public boolean hasUnavailableContent() {
    return hasUnavailableContent(new ArrayList<Entry>());
  }

  public boolean hasUnavailableContent(List<Entry> entriesWithUnavailableContent) {
    return false;
  }

  public Entry getParent() {
    return myParent;
  }

  protected void setParent(DirectoryEntry parent) {
    myParent = parent;
  }

  public boolean isDirectory() {
    return false;
  }

  public void addChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException(formatAddRemove(child));
  }

  private String formatAddRemove(Entry child) {
    return "add/remove " + child.formatPath() + " to " + formatPath();
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

  public abstract Entry copy();

  public void changeName(String newName) {
    if (myParent != null) myParent.checkDoesNotExist(this, newName);
    myName = newName;
  }

  public void changeContent(Content newContent, long timestamp) {
    throw new UnsupportedOperationException(formatPath());
  }           

  public List<Difference> getDifferencesWith(Entry e) {
    List<Difference> result = new ArrayList<Difference>();
    collectDifferencesWith(e, result);
    return result;
  }

  public abstract void collectDifferencesWith(Entry e, List<Difference> result);

  protected abstract void collectCreatedDifferences(List<Difference> result);

  protected abstract void collectDeletedDifferences(List<Difference> result);

  @Override
  public String toString() {
    return String.valueOf(myId) + "-" + myName;
  }

  private String formatPath() {
    String type = isDirectory() ? "dir: " : "file: ";
    return type + getPath();
  }
}
