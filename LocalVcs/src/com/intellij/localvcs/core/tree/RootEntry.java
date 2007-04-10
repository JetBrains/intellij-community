package com.intellij.localvcs.core.tree;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;

import java.io.IOException;
import java.util.List;

// todo try to crean up Entry hierarchy
// todo replace all String.length() == 0 with String.isEmpty()
// todo maybe get rid of create/delete/remove methods
// todo rename this class... to something other then RootEntry

public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super(-1, "");
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
  }

  protected IdPath getIdPathAppendedWith(int id) {
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

  @Override
  public Entry findEntry(IdPath path) {
    return searchInChildren(path);
  }

  public Entry createFile(int id, String path, Content content, long timestamp) {
    FileEntry e = new FileEntry(id, Paths.getNameOf(path), content, timestamp);
    addEntry(Paths.getParentOf(path), e);
    return e;
  }

  public void createFile(int id, IdPath parentPath, String name, Content content, long timestamp) {
    FileEntry e = new FileEntry(id, name, content, timestamp);
    addEntry(parentPath, e);
  }

  public Entry createDirectory(int id, String path) {
    // todo messsssss!!!! should introduce createRoot method instead?
    // todo and simplify addEntry method too? 
    String name = Paths.getNameOf(path);
    String parentPath = Paths.getParentOf(path);

    if (parentPath == null || !hasEntry(parentPath)) { // is it suppose to be a root?
      parentPath = null;
      name = path;
    }

    DirectoryEntry e = new DirectoryEntry(id, name);
    addEntry(parentPath, e);

    return e;
  }

  public void createDirectory(int id, IdPath parentPath, String name) {
    DirectoryEntry e = new DirectoryEntry(id, name);
    addEntry(parentPath, e);
  }

  // todo OBSOLETE METHOD!!!
  public void changeFileContent(String path, Content newContent, long newTimestamp) {
    Entry e = getEntry(path);
    e.changeContent(newContent, newTimestamp);
  }

  public void changeFileContent(IdPath path, Content newContent, long newTimestamp) {
    Entry e = getEntry(path);
    e.changeContent(newContent, newTimestamp);
  }

  public void rename(String path, String newName) {
    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    Entry e = getEntry(path);
    e.changeName(Paths.renamed(e.getName(), newName));
  }

  public void rename(IdPath path, String newName) {
    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    Entry e = getEntry(path);
    e.changeName(Paths.renamed(e.getName(), newName));
  }

  public void move(String path, String newParentPath) {
    Entry e = getEntry(path);
    removeEntry(e);
    addEntry(newParentPath, e);
  }

  public void move(IdPath path, IdPath newParentPath) {
    Entry e = getEntry(path);
    removeEntry(e);
    addEntry(newParentPath, e);
  }

  public void delete(String path) {
    removeEntry(getEntry(path));
  }

  public void delete(IdPath path) {
    removeEntry(getEntry(path));
  }

  private void addEntry(String parentPath, Entry entry) {
    // todo try to remove this conditional logic
    Entry parent = parentPath == null ? this : getEntry(parentPath);
    parent.addChild(entry);
  }

  private void addEntry(IdPath parentPath, Entry entry) {
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
