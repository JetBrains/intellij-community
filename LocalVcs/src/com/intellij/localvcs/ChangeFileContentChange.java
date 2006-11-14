package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private String myOldContent;
  private IdPath myAffectedEntryIdPath;

  public ChangeFileContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    myPath = s.readPath();
    myAffectedEntryIdPath = s.readIdPath();
    myNewContent = s.readString();
    myOldContent = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeIdPath(myAffectedEntryIdPath);
    s.writeString(myNewContent);
    s.writeString(myOldContent);
  }

  public Path getPath() {
    return myPath;
  }

  public String getNewContent() {
    return myNewContent;
  }

  public String getOldContent() {
    return myOldContent;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldContent = affectedEntry.getContent();
    myAffectedEntryIdPath = affectedEntry.getIdPath();

    root.doChangeFileContent(affectedEntry.getId(), myNewContent);
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doChangeFileContent(myAffectedEntryIdPath.getName(), myOldContent);
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myAffectedEntryIdPath);
  }

  @Override
  public List<Difference> getDifferences(RootEntry r, Entry e) {
    if (!affects(e)) return Collections.emptyList();

    Entry current = r.getEntry(myAffectedEntryIdPath.getName());
    Entry older = new FileEntry(null, myPath.getName(), myNewContent);

    return Collections.singletonList(new Difference(Difference.Kind.MODIFIED,
                                                    older,
                                                    current));
  }
}
