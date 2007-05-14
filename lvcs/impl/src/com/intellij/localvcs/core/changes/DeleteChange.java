package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeleteChange extends StructuralChange {
  private Entry myAffectedEntry;

  public DeleteChange(String path) {
    super(path);
  }

  public DeleteChange(Stream s) throws IOException {
    super(s);
    myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeEntry(myAffectedEntry);
  }

  public Entry getAffectedEntry() {
    return myAffectedEntry;
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    myAffectedEntry = root.getEntry(myPath);
    IdPath idPath = myAffectedEntry.getIdPath();

    root.delete(idPath);

    return idPath;
  }

  @Override
  public void revertOn(RootEntry root) {
    restoreEntryRecursively(root, myAffectedEntry, myAffectedIdPath.getParent());
  }

  private void restoreEntryRecursively(RootEntry root, Entry e, IdPath parentPath) {
    // todo try to cleanup this mess
    if (e.isDirectory()) {
      root.createDirectory(e.getId(), parentPath, e.getName());
      // todo could parentPath be null????
      parentPath = parentPath == null ? e.getIdPath() : parentPath.appendedWith(e.getId());

      for (Entry child : e.getChildren()) {
        restoreEntryRecursively(root, child, parentPath);
      }
    }
    else {
      root.createFile(e.getId(), parentPath, e.getName(), e.getContent(), e.getTimestamp());
    }
  }

  @Override
  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<Content>();
    collectContentsRecursively(myAffectedEntry, result);
    return result;
  }

  private void collectContentsRecursively(Entry e, List<Content> result) {
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) {
        collectContentsRecursively(child, result);
      }
    }
    else {
      result.add(e.getContent());
    }
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
