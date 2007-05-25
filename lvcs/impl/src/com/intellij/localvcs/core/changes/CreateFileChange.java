package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.FileEntry;

import java.io.IOException;

public class CreateFileChange extends StructuralChange {
  private int myId; // transient
  private Content myContent; // transient
  private long myTimestamp; // transient

  public CreateFileChange(int id, String path, Content content, long timestamp) {
    super(path);
    myId = id;
    myContent = content;
    myTimestamp = timestamp;
  }

  public CreateFileChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    String name = Paths.getNameOf(myPath);
    String parentPath = Paths.getParentOf(myPath);

    Entry e = new FileEntry(myId, name, myContent, myTimestamp);
    Entry parent = parentPath == null ? r : r.findEntry(parentPath);
    parent.addChild(e);

    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry root) {
    Entry e = root.getEntry(myAffectedIdPath);
    e.getParent().removeChild(e);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return e.getId() == myAffectedIdPath.getId();
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
