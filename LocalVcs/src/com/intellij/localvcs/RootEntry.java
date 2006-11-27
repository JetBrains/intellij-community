package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

// todo try to crean up Entry hierarchy
public class RootEntry extends DirectoryEntry {
  // todo try to remove different null-checks
  public RootEntry() {
    super(null, null, null);
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  // todo it seems that we can get rid of these two methods
  protected IdPath getIdPathAppendedWith(Integer id) {
    // todo test it
    return new IdPath(id);// todo verify this method.
  }

  protected String getPathAppendedWith(String name) {
    return name;
  }

  public boolean hasEntry(String path) {
    return findEntry(path) != null;
  }

  private boolean hasEntry(Integer id) {
    return findEntry(id) != null;
  }

  protected Entry findEntry(String path) {
    return findEntry(new PathMatcher(path));
  }

  private Entry findEntry(Integer id) {
    // todo get rid of this method
    return findEntry(new IdMatcher(id));
  }


  public Entry getEntry(String path) {
    return getEntry(new PathMatcher(path));
  }

  public Entry getEntry(Integer id) {
    return getEntry(new IdMatcher(id));
  }

  private Entry getEntry(Matcher m) {
    // todo get rid of this method
    Entry result = findEntry(m);
    // todo should we raise more meaningfull exception here?
    assert result != null;
    return result;
  }

  public List<Entry> getRoots() {
    return getChildren();
  }

  public void createFile(Integer id, String path, String content, Long timestamp) {
    FileEntry e = new FileEntry(id, Path.getNameOf(path), content, timestamp);
    addEntry(Path.getParentOf(path), e);
  }

  public void createDirectory(Integer id, String path, Long timestamp) {
    // todo messsssss!!!! should we introduce createRoot method instead?
    // todo and simplify addEntry method too? 
    String name = Path.getNameOf(path);
    String parentPath = Path.getParentOf(path);

    if (parentPath == null || !hasEntry(parentPath)) { // is it suppose to be a root?
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
    addEntry(Path.getParentOf(path), newEntry);
  }

  public void rename(String path, String newName) {
    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.renamed(newName);

    removeEntry(oldEntry);
    addEntry(Path.getParentOf(path), newEntry);
  }

  public void move(String path, String newParentPath) {
    Entry e = getEntry(path);
    removeEntry(e);
    addEntry(newParentPath, e);
  }

  public void delete(String path) {
    removeEntry(getEntry(path));
  }

  private void addEntry(String parentPath, Entry entry) {
    // todo this check is just for testing...
    assert entry.getId() == null || !hasEntry(entry.getId());

    // todo try to remove this conditional logic
    Entry parent = parentPath == null ? this : getEntry(parentPath);
    parent.addChild(entry);
  }

  private void removeEntry(Entry e) {
    Entry parent = e.getParent() == null ? this : e.getParent();
    parent.removeChild(e);
  }

  @Override
  public RootEntry copy() {
    // todo just for avoid casting so try to remove it
    return (RootEntry)super.copy();
  }

  @Override
  protected DirectoryEntry copyEntry() {
    return new RootEntry(); //  todo test copying!!!
  }

  private static class PathMatcher implements Matcher {
    // todo optimize it
    private String myPath;

    public PathMatcher(String path) {
      myPath = path;
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
