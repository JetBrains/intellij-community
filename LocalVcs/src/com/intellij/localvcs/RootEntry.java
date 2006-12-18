package com.intellij.localvcs;

import java.io.IOException;
import java.util.List;

// todo try to crean up Entry hierarchy
// todo replace all String.length() == 0 with String.isEmpty()
// todo maybe get rid of create/delete/remove methods

// TODO make entries to be modifiable objects
// todo rename this class... to something other then RootEntry
public class RootEntry extends DirectoryEntry {
  // todo try to remove different null-checks
  public RootEntry() {
    super(null, null, null);
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    return new IdPath(id);
  }

  protected String getPathAppendedWith(String name) {
    return name;
  }

  public List<Entry> getRoots() {
    return getChildren();
  }

  @Override
  public Entry findEntry(String path) {
    return searchInChildren(path);
  }

  public void createFile(Integer id, String path, Content content, Long timestamp) {
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

  public void changeFileContent(String path, Content newContent, Long newTimestamp) {
    Entry e = getEntry(path);
    e.changeContent(newContent, newTimestamp);
  }

  public void rename(String path, String newName) {
    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    Entry e = getEntry(path);
    e.changeName(Path.renamed(e.getName(), newName));
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
    return new RootEntry();
  }
}
