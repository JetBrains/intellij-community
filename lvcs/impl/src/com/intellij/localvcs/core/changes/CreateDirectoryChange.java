package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.DirectoryEntry;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;

public class CreateDirectoryChange extends StructuralChange {
  private int myId; // transient

  public CreateDirectoryChange(int id, String path) {
    super(path);
    myId = id;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    // todo messsssss!!!! should introduce createRoot method instead?
    // todo and simplify addEntry method too?
    String name = Paths.getNameOf(myPath);
    String parentPath = Paths.getParentOf(myPath);

    if (parentPath == null || !r.hasEntry(parentPath)) { // is it supposed to be a root?
      parentPath = null;
      name = myPath;
    }

    DirectoryEntry e = new DirectoryEntry(myId, name);
    Entry parent = parentPath == null ? r : r.findEntry(parentPath);
    parent.addChild(e);

    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry r) {
    Entry e = r.getEntry(myAffectedIdPath);
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
