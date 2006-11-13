package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParent;
  private IdPath myFromIdPath;
  private IdPath myToIdPath;

  public MoveChange(Path path, Path newParent) {
    myPath = path;
    myNewParent = newParent;
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewParent = s.readPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writePath(myNewParent);
  }

  public Path getPath() {
    return myPath;
  }

  public Path getNewParent() {
    return myNewParent;
  }

  @Override
  public void applyTo(RootEntry root) {
    myFromIdPath = root.getEntry(myPath).getIdPath();
    root.doMove(myPath, myNewParent);
    myToIdPath = root.getEntry(getNewPath()).getIdPath();
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doMove(getNewPath(), myPath.getParent());
  }

  private Path getNewPath() {
    return myNewParent.appendedWith(myPath.getName());
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myFromIdPath, myToIdPath);
  }

  @Override
  public List<Difference> getDifferencesFor(Entry e) {
    if (!affects(e)) return Collections.emptyList();

    if (myFromIdPath.getName().equals(e.getId()))
      return Collections
          .singletonList(new Difference(Difference.Kind.MODIFIED));

    List<Difference> result = new ArrayList<Difference>();

    if (myFromIdPath.contains(e.getId()))
      result.add(new Difference(Difference.Kind.DELETED));

    if (myToIdPath.contains(e.getId()))
      result.add(new Difference(Difference.Kind.CREATED));

    return result;
  }
}
