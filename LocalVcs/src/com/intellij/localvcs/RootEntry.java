package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

// todo try to crean up Entry hierarchy
public class RootEntry extends DirectoryEntry {
  // todo try to remove difference null-checks
  public RootEntry() {
    super(null, null, null);
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  // todo it seems that we can get rid of these two methods 
  protected Path getPathAppendedWith(String name) {
    return new Path(name);
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    return new IdPath(id);// todo verify this method.
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

  public List<Entry> getRoots() {
    return getChildren();
  }

  public void createFile(Integer id, String path, String content, Long timestamp) {
    FileEntry e = new FileEntry(id, new Path(path).getName(), content, timestamp);
    addEntry(new Path(path).getParent(), e);
  }

  public void createDirectory(Integer id, String path, Long timestamp) {
    // todo messsssss!!!!
    Path p = new Path(path);
    Path parentPath = p.getParent();
    String name = p.getName();

    if (parentPath == null || !hasEntry(parentPath.getPath()))  {
      parentPath = null;
      name = path;
    }

    DirectoryEntry e = new DirectoryEntry(id, name, timestamp);
    addEntry(parentPath, e);
  }

  // todo make entries to be modifiable objects

  public void changeFileContent(String path, String newContent, Long timestamp) {
    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.withContent(newContent, timestamp);

    removeEntry(oldEntry);
    addEntry(new Path(path).getParent(), newEntry);
  }

  public void rename(String path, String newName, Long timestamp) {
    // todo maybe remove this check?
    if (newName.equals(new Path(path).getName())) return;

    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.renamed(newName, timestamp);

    removeEntry(oldEntry);
    addEntry(new Path(path).getParent(), newEntry);
  }

  public void move(String path, String newParentPath, Long timestamp) {
    Entry e = getEntry(path);
    e.setTimestamp(timestamp); // todo it smells bed

    removeEntry(e);
    addEntry(new Path(newParentPath), e);
  }

  public void delete(String path) {
    removeEntry(getEntry(path));
  }

  private void addEntry(Path parentPath, Entry entry) {
    // todo chenge parameter from path to id or better to string
    // todo just for testing...
    assert entry.getId() == null || !hasEntry(entry.getId());

    // todo try to remove this logic
    Entry parent = parentPath == null ? this : getEntry(parentPath.getPath());
    parent.addChild(entry);
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
    return new RootEntry(); //  todo test copying!!!
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
