package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public abstract class ChangeRevertionVisitor extends ChangeVisitor {
  protected RootEntry myRootEntry;
  private IdeaGateway myGateway;

  public ChangeRevertionVisitor(ILocalVcs vcs, IdeaGateway gw) {
    myRootEntry = vcs.getRootEntry().copy();
    myGateway = gw;
  }

  public void visit(ChangeSet c) throws Exception {
  }

  public void visit(CreateFileChange c) throws Exception {
    revertCreation(c);
  }

  public void visit(CreateDirectoryChange c) throws Exception {
    revertCreation(c);
  }

  private void revertCreation(StructuralChange c) throws Exception {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    if (shouldProcess(c)) {
      f.delete(null);
    }

    c.revertOn(myRootEntry);
  }

  public void visit(ChangeFileContentChange c) throws Exception {
    c.revertOn(myRootEntry);

    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    if (shouldProcess(c)) {
      f.setBinaryContent(e.getContent().getBytes(), -1, e.getTimestamp());
    }
  }

  public void visit(RenameChange c) throws Exception {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRootEntry);

    if (shouldProcess(c)) {
      f.rename(null, e.getName());
    }
  }

  public void visit(MoveChange c) throws Exception {
    Entry e = getAffectedEntry(c, 1);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRootEntry);
    Entry parentEntry = getAffectedEntry(c).getParent();
    VirtualFile parent = myGateway.findVirtualFile(parentEntry.getPath());

    if (shouldProcess(c)) {
      f.move(null, parent);
    }
  }

  public void visit(DeleteChange c) throws Exception {
    c.revertOn(myRootEntry);
    Entry e = getAffectedEntry(c);

    if (shouldProcess(c)) {
      revertDeletion(e);
    }
  }

  private void revertDeletion(Entry e) throws IOException {
    VirtualFile parent = myGateway.findVirtualFile(e.getParent().getPath());
    if (e.isDirectory()) {
      parent.createChildDirectory(null, e.getName());
      for (Entry child : e.getChildren()) revertDeletion(child);
    }
    else {
      VirtualFile f = parent.createChildData(null, e.getName());
      f.setBinaryContent(e.getContent().getBytes(), -1, e.getTimestamp());
    }
  }

  protected abstract boolean shouldProcess(Change c);

  private Entry getAffectedEntry(StructuralChange c) {
    return getAffectedEntry(c, 0);
  }

  private Entry getAffectedEntry(StructuralChange c, int i) {
    return myRootEntry.getEntry(c.getAffectedIdPaths()[i]);
  }
}
