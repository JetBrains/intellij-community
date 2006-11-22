package com.intellij.localvcs;

import java.io.IOException;

// todo try to crean up Entry hierarchy
public class RootEntry extends DirectoryEntry {
  public RootEntry(String path) {
    super(null, path, null);
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  public void setPath(String path) {
    // todo refactor path stuffs
    myName = path;
  }

  // todo it seems that we can get rid of these two methods 
  protected Path getPathAppendedWith(String name) {
    return new Path(getName()).appendedWith(name);
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    return new IdPath(id);
  }

  public boolean hasEntry(String path) {
    return findEntry(new Path(path)) != null;
  }

  protected Entry findEntry(Path path) {
    return findEntry(new PathMatcher(path));
  }

  public Entry findEntry(Integer id) {
    return findEntry(new IdMatcher(id));
  }

  public boolean hasEntry(Integer id) {
    return findEntry(id) != null;
  }

  public Entry getEntry(String path) {
    return getEntry(new PathMatcher(new Path(path)));
  }

  public Entry getEntry(Integer id) {
    return getEntry(new IdMatcher(id));
  }

  private Entry getEntry(Matcher m) {
    Entry result = findEntry(m);
    if (result == null) throw new LocalVcsException();
    return result;
  }

  public void createFile(Integer id, String path, String content, Long timestamp) {
    FileEntry e = new FileEntry(id, new Path(path).getName(), content, timestamp);
    addEntry(new Path(path).getParent().getPath(), e);
  }

  public void createDirectory(Integer id, String path, Long timestamp) {
    DirectoryEntry e = new DirectoryEntry(id, new Path(path).getName(), timestamp);
    addEntry(new Path(path).getParent().getPath(), e);
  }

  // todo make entries to be modifiable objects

  public void changeFileContent(String path, String newContent, Long timestamp) {
    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.withContent(newContent, timestamp);

    removeEntry(oldEntry);
    addEntry(new Path(path).getParent().getPath(), newEntry);
  }

  public void rename(String path, String newName, Long timestamp) {
    // todo maybe remove this check?
    if (newName.equals(new Path(path).getName())) return;

    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.renamed(newName, timestamp);

    removeEntry(oldEntry);
    addEntry(new Path(path).getParent().getPath(), newEntry);
  }

  public void move(String path, String newParentPath, Long timestamp) {
    Entry e = getEntry(path);
    e.setTimestamp(timestamp); // todo it smells bed

    removeEntry(e);
    addEntry(newParentPath, e);
  }

  public void delete(String path) {
    removeEntry(getEntry(path));
  }

  private void addEntry(String parent, Entry entry) {
    // todo chenge parameter from path to id
    // todo just for testing...
    assert entry.getId() == null || !hasEntry(entry.getId());

    // todo it's quite ugly
    // todo it seems that we can remove all such check now since we have named root entry 
    if (parent == null) {
      addChild(entry);
    }
    else {
      getEntry(parent).addChild(entry);
    }
  }

  private void removeEntry(Entry e) {
    Entry parent = e.getParent() == null ? this : e.getParent();
    parent.removeChild(e);
  }

  @Override
  public RootEntry copy() {
    // todo just for avoid casting
    return (RootEntry)super.copy();
  }

  @Override
  protected DirectoryEntry copyEntry() {
    return new RootEntry(""); //  todo test copying!!!
  }

  private static class PathMatcher implements Matcher {
    // todo optimize it
    private Path myPath;

    public PathMatcher(Path p) {
      myPath = p;
    }

    public boolean matches(Entry e) {
      return myPath.equals(e.getPath());
    }
  }

  private static class IdMatcher implements Matcher {
    private Integer myId;

    public IdMatcher(Integer id) {
      myId = id;
    }

    public boolean matches(Entry e) {
      return myId.equals(e.myId);
    }
  }
}
