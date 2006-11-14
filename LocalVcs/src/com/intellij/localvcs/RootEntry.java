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

  public void doCreateFile(Integer id, Path path, String content) {
    addEntry(path.getParent(), new FileEntry(id, path.getName(), content));
  }

  public void doCreateDirectory(Integer id, Path path) {
    addEntry(path.getParent(), new DirectoryEntry(id, path.getName()));
  }

  public void doChangeFileContent(Integer id, String content) {
    Entry oldEntry = getEntry(id);

    Entry newEntry = new FileEntry(oldEntry.getId(),
                                   oldEntry.getName(),
                                   content);

    Path path = oldEntry.getPath();
    removeEntry(id);
    addEntry(path.getParent(), newEntry);
  }

  public void doRename(Integer id, String newName) {
    Entry oldEntry = getEntry(id);
    if (newName.equals(oldEntry.getName())) return;

    Entry newEntry = oldEntry.renamed(newName);

    Path path = oldEntry.getPath();

    removeEntry(id);
    addEntry(path.getParent(), newEntry);
  }

  public void doMove(Integer id, Path parent) {
    Entry e = getEntry(id);

    removeEntry(id);
    addEntry(parent, e);
  }

  public void doDelete(Integer id) {
    removeEntry(id);
  }

  private void addEntry(Path parent, Entry entry) {
    // todo chenge parameter from path to id
    // todo just for testing...
    assert entry.getId() == null || !hasEntry(entry.getId());

    // todo it's quite ugly
    if (parent == null) addChild(entry);
    else getEntry(parent).addChild(entry);
  }

  private void removeEntry(Integer id) {
    Entry e = getEntry(id);
    Entry parent = e.getParent() == null ? this : e.getParent();
    parent.removeChild(e);
  }

  public void apply(ChangeSet cs) {
    cs.applyTo(this);
  }

  public RootEntry revert(ChangeSet cs) {
    // todo maybe revert should not return copy too 
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
  protected DirectoryEntry copyEntry() {
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

    public boolean matches(Entry e) { return myId.equals(e.myId); }
  }
}
