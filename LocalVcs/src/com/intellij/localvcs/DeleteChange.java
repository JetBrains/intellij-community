package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DeleteChange extends Change {
  private Path myPath;
  private Entry myAffectedEntry;
  private IdPath myAffectedEntryIdPath;

  public DeleteChange(Path path) {
    myPath = path;
  }

  public DeleteChange(Stream s) throws IOException {
    myPath = s.readPath();
    myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeEntry(myAffectedEntry);
  }

  public Path getPath() {
    return myPath;
  }

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath);
    myAffectedEntryIdPath = myAffectedEntry.getIdPath();

    root.doDelete(myAffectedEntryIdPath.getName());
  }

  @Override
  public void revertOn(RootEntry root) {
    // todo maybe we should create several DeleteChanges instead of saving
    // todo previous entry?
    restoreEntryRecursively(root, myAffectedEntry, myPath);
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, Path p) {
    if (e.isDirectory()) {
      root.doCreateDirectory(e.getId(), p);
      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, p.appendedWith(child.getName()));
      }
    } else {
      root.doCreateFile(e.getId(), p, e.getContent());
    }
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }

  @Override
  public List<Difference> getDifferences(RootEntry r, Entry e) {
    if (!affects(e)) return Collections.emptyList();
    return Collections.singletonList(new Difference(Difference.Kind.DELETED));
  }
}
