package com.intellij.localvcs;

import java.io.IOException;

// todo try to crean up Entry hierarchy
public class RootEntry extends DirectoryEntry {
  private Integer myChangeListIndex = -1;

  public RootEntry(String name) {
    super(null, name);
  }

  public RootEntry(Stream s) throws IOException {
    super(s);
    myChangeListIndex = s.readInteger();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeInteger(myChangeListIndex);
  }

  public Integer getChangeListIndex() {
    return myChangeListIndex;
  }

  public boolean canBeReverted() {
    // todo bad methdo
    return myChangeListIndex >= 0;
  }

  public void incrementChangeListIndex() {
    myChangeListIndex++;
  }

  public void decrementChangeListIndex() {
    myChangeListIndex--;
  }

  protected IdPath getIdPathAppendedWith(Integer id) {
    return new IdPath(id);
  }

  protected Path getPathAppendedWith(String name) {
    return new Path(myName).appendedWith(name);

    //return new Path(name);
  }

  public boolean hasEntry(Path path) {
    return findEntry(path) != null;
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

  public Entry getEntry(Path path) {
    return getEntry(new PathMatcher(path));
  }

  public Entry getEntry(Integer id) {
    return getEntry(new IdMatcher(id));
  }

  private Entry getEntry(Matcher m) {
    Entry result = findEntry(m);
    if (result == null) throw new LocalVcsException();
    return result;
  }

  public void doCreateFile(Integer id, Path path, String content) {
    addEntry(path.getParent(), new FileEntry(id, path.getName(), content));
  }

  public void doCreateDirectory(Integer id, Path path) {
    addEntry(path.getParent(), new DirectoryEntry(id, path.getName()));
  }

  public void doChangeFileContent(Path path, String newContent) {
    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.withContent(newContent);

    removeEntry(oldEntry);
    addEntry(path.getParent(), newEntry);
  }

  public void doRename(Path path, String newName) {
    // todo maybe remove this check?
    if (newName.equals(path.getName())) return;

    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.renamed(newName);

    removeEntry(oldEntry);
    addEntry(path.getParent(), newEntry);
  }

  public void doMove(Path path, Path parent) {
    Entry e = getEntry(path);
    removeEntry(e);
    addEntry(parent, e);
  }

  public void doDelete(Path path) {
    removeEntry(getEntry(path));
  }

  private void addEntry(Path parent, Entry entry) {
    // todo chenge parameter from path to id
    // todo just for testing...
    assert entry.getId() == null || !hasEntry(entry.getId());

    // todo it's quite ugly
    if (parent == null) addChild(entry);
    else getEntry(parent).addChild(entry);
  }

  private void removeEntry(Entry e) {
    Entry parent = e.getParent() == null ? this : e.getParent();
    parent.removeChild(e);
  }

  public void apply_old(ChangeSet cs) {
    cs.applyTo(this);
  }

  public RootEntry revert_old(ChangeSet cs) {
    // todo maybe revert should not return copy too 
    RootEntry result = copy();
    cs._revertOn(result);
    return result;
  }

  @Override
  public RootEntry copy() {
    // todo just for avoid casting
    return (RootEntry)super.copy();
  }

  @Override
  protected DirectoryEntry copyEntry() {
    RootEntry result = new RootEntry("");
    // todo test it
    result.myChangeListIndex = myChangeListIndex;
    return result;
  }

  private static class PathMatcher implements Matcher {
    // todo optimize it
    private Path myPath;

    public PathMatcher(Path p) { myPath = p; }

    public boolean matches(Entry e) { return myPath.equals(e.getPath()); }
  }

  private static class IdMatcher implements Matcher {
    private Integer myId;

    public IdMatcher(Integer id) { myId = id; }

    public boolean matches(Entry e) { return myId.equals(e.myId); }
  }
}
