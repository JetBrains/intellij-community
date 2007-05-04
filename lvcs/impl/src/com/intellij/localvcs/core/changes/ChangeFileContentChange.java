package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ChangeFileContentChange extends StructuralChange {
  private Content myNewContent; // transient
  private Content myOldContent;
  private long myNewTimestamp; // transient
  private long myOldTimestamp;

  public ChangeFileContentChange(String path, Content newContent, long timestamp) {
    super(path);
    myNewContent = newContent;
    myNewTimestamp = timestamp;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    super(s);
    myOldContent = s.readContent();
    myOldTimestamp = s.readLong();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeContent(myOldContent);
    s.writeLong(myOldTimestamp);
  }

  public Content getOldContent() {
    return myOldContent;
  }

  public long getOldTimestamp() {
    return myOldTimestamp;
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldContent = affectedEntry.getContent();
    myOldTimestamp = affectedEntry.getTimestamp();

    IdPath idPath = affectedEntry.getIdPath();
    root.changeFileContent(idPath, myNewContent, myNewTimestamp);
    return idPath;
  }

  @Override
  public void revertOn(RootEntry root) {
    root.changeFileContent(myAffectedIdPath, myOldContent, myOldTimestamp);
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.singletonList(myOldContent);
  }

  @Override
  public void accept(ChangeVisitor v) throws Exception {
    v.visit(this);
  }
}
