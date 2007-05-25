package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;

public abstract class CreateEntryChange extends StructuralChange {
  protected int myId; // transient

  public CreateEntryChange(int id, String path) {
    super(path);
    myId = id;
  }

  public CreateEntryChange(Stream s) throws IOException {
    super(s);
  }

  protected IdPath addEntry(Entry r, String parentPath, Entry e) {
    Entry parent = parentPath == null ? r : r.findEntry(parentPath);
    parent.addChild(e);
    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry root) {
    Entry e = root.getEntry(myAffectedIdPath);
    removeEntry(e);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return e.getId() == myAffectedIdPath.getId();
  }
}
