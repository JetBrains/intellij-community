package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParentPath;
  private IdPath myFromIdPath;
  private IdPath myToIdPath;

  public MoveChange(String path, String newParentPath) {
    myPath = new Path(path);
    myNewParentPath = new Path(newParentPath);
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewParentPath = s.readPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writePath(myNewParentPath);
  }

  public Path getPath() {
    return myPath;
  }

  public Path getNewParentPath() {
    return myNewParentPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    myFromIdPath = root.getEntry(myPath.getPath()).getIdPath();
    root.move(myPath.getPath(), myNewParentPath.getPath(), null); // todo set timestamp here!!!
    myToIdPath = root.getEntry(getNewPath().getPath()).getIdPath();
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.move(myNewParentPath.appendedWith(myPath.getName()).getPath(),
                myPath.getParent().getPath(), null); 
  }

  private Path getNewPath() {
    return myNewParentPath.appendedWith(myPath.getName());
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myFromIdPath, myToIdPath);
  }

  //@Override
  //public List<Difference> getDifferences(RootEntry r, Entry e) {
  //  if (!affects(e)) return Collections.emptyList();
  //
  //  if (myFromIdPath.getName().equals(e.getId())) {
  //    Difference d = new Difference(Difference.Kind.MODIFIED);
  //    return Collections.singletonList(d);
  //  }
  //
  //  List<Difference> result = new ArrayList<Difference>();
  //
  //  if (myFromIdPath.contains(e.getId()))
  //    result.add(new Difference(Difference.Kind.DELETED));
  //
  //  if (myToIdPath.contains(e.getId()))
  //    result.add(new Difference(Difference.Kind.CREATED));
  //
  //  return result;
  //}
}
