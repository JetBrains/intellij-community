package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MoveChange extends Change {
  private String myPath;
  private String myNewParentPath;
  private IdPath myFromIdPath;
  private IdPath myToIdPath;

  public MoveChange(String path, String newParentPath) {
    myPath = path;
    myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readString();
    myNewParentPath = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeString(myNewParentPath);
  }

  public String getPath() {
    return myPath;
  }

  public String getNewParentPath() {
    return myNewParentPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    myFromIdPath = root.getEntry(myPath).getIdPath();
    root.move(myPath, myNewParentPath, null); // todo set timestamp here!!!
    myToIdPath = root.getEntry(getNewPath()).getIdPath();
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.move(getNewPath(), new Path(myPath).getParent().getPath(), null); 
  }

  private String getNewPath() {
    return new Path(myNewParentPath).appendedWith(new Path(myPath).getName()).getPath();
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
