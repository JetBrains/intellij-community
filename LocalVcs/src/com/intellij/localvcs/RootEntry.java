package com.intellij.localvcs;

import java.io.IOException;

// todo try to crean up Entry hierarchy
public class RootEntry extends DirectoryEntry {
  private Integer myChangeListIndex = -1;

  public RootEntry() {
    super(null, null);
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
    return myChangeListIndex >= 0;
  }

  public void incrementChangeListIndex() {
    myChangeListIndex++;
  }

  public void decrementChangeListIndex() {
    myChangeListIndex--;
  }

  protected Path getPathAppendedWith(String name) {
    return new Path(name);
  }

  public boolean hasEntry(Path path) {
    return findEntry(path) != null;
  }

  protected Entry findEntry(Path path) {
    return findEntry(new PathMatcher(path));
  }

  public boolean hasEntry(Integer id) {
    return findEntry(new IdMatcher(id)) != null;
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

  protected void doCreateFile(Path path, String content, Integer id) {
    addEntry(path.getParent(), new FileEntry(id, path.getName(), content));
  }

  protected void doCreateDirectory(Path path, Integer id) {
    addEntry(path.getParent(), new DirectoryEntry(id, path.getName()));
  }

  protected void doChangeFileContent(Path path, String content) {
    Entry oldEntry = getEntry(path);
    Entry newEntry = new FileEntry(oldEntry.getObjectId(),
                                   path.getName(),
                                   content);

    removeEntry(path);
    addEntry(path.getParent(), newEntry);
  }

  protected void doRename(Path path, String newName) {
    if (newName.equals(path.getName())) return;

    Entry oldEntry = getEntry(path);
    Entry newEntry = oldEntry.renamed(newName);

    removeEntry(path);
    addEntry(path.getParent(), newEntry);
  }

  public void doMove(Path path, Path parent) {
    Entry e = getEntry(path);

    removeEntry(path);
    addEntry(parent, e);
  }

  protected void doDelete(Path path) {
    removeEntry(path);
  }

  private void addEntry(Path parent, Entry entry) {
    // todo it's quite ugly
    if (parent == null) addChild(entry);
    else getEntry(parent).addChild(entry);
  }

  private void removeEntry(Path path) {
    Entry parent = path.isRoot() ? this : getEntry(path.getParent());
    parent.removeChild(getEntry(path));
  }

  public RootEntry apply(ChangeSet cs) {
    // todo should we really make a copy here?
    RootEntry result = copy();
    cs.applyTo(result);
    return result;
  }

  public RootEntry revert(ChangeSet cs) {
    RootEntry result = copy();
    cs.revertOn(result);
    return result;
  }

  @Override
  public RootEntry copy() {
    // todo just for avoid casting
    return (RootEntry)super.copy();
  }

  @Override
  protected Entry copyEntry() {
    RootEntry result = new RootEntry();
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

    public boolean matches(Entry e) { return myId.equals(e.myObjectId); }
  }
}
