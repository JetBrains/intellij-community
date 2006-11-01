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
    return findEntry(new PathMatcher(path));
  }

  public Entry findEntry(Integer id) {
    return findEntry(new IdMatcher(id));
  }

  public Entry findEntry(Matcher m) {
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

  // todo make them all private by replacing clients with RootEntry 
  public interface Matcher {
    boolean matches(Entry entry);
  }

  public static class PathMatcher implements Matcher {
    // todo optimize it
    private Path myPath;

    public PathMatcher(Path p) { myPath = p; }

    public boolean matches(Entry e) { return e.getPath().equals(myPath); }
  }

  public static class IdMatcher implements Matcher {
    private Integer myId;

    public IdMatcher(Integer id) { myId = id; }

    public boolean matches(Entry e) { return e.myObjectId.equals(myId); }
  }
}
