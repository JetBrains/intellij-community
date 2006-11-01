package com.intellij.localvcs;

import java.util.List;

public abstract class Entry {
  //todo try to make fields final
  protected Integer myObjectId;
  protected String myName;
  protected DirectoryEntry myParent;

  public Entry(Integer objectId, String name) {
    myObjectId = objectId;
    myName = name;
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public String getName() {
    return myName;
  }

  public Path getPath() {
    if (!hasParent()) return new Path(myName);
    return myParent.getPath().appendedWith(myName);
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
    throw new UnsupportedOperationException();
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException();
  }

  public List<Entry> getChildren() {
    throw new UnsupportedOperationException();
  }

  public Entry findEntry(Path path) {
    final Path finalPath = path;
    return findEntry(new Matcher() {
      @Override
      public boolean matches(Entry entry) {
        // todo optimize it
        return entry.getPath().equals(finalPath);
      }
    });
  }

  public Entry findEntry(Integer id) {
    final Integer finalId = id;
    return findEntry(new Matcher() {
      @Override
      public boolean matches(Entry entry) {
        return entry.myObjectId.equals(finalId);
      }
    });
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

  @Override
  public String toString() {
    return getClass().getSimpleName()
           + "(id: " + myObjectId + ", "
           + "name: " + myName + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    Entry e = (Entry)o;
    return myObjectId.equals(e.myObjectId) && myName.equals(e.myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }

  protected abstract class Matcher {
    public boolean matches(Entry entry) { return true; }
  }
}
