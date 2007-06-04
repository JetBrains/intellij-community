package com.intellij.localvcs.integration.revertion;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.changes.*;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public class ChangeRevertionVisitor extends ChangeVisitor {
  private Entry myRootEntry;
  private IdeaGateway myGateway;

  public ChangeRevertionVisitor(ILocalVcs vcs, IdeaGateway gw) {
    myRootEntry = vcs.getRootEntry().copy();
    myGateway = gw;
  }

  @Override
  public void visit(CreateFileChange c) throws IOException {
    revertCreation(c);
  }

  @Override
  public void visit(CreateDirectoryChange c) throws IOException {
    revertCreation(c);
  }

  private void revertCreation(StructuralChange c) throws IOException {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    f.delete(null);

    c.revertOn(myRootEntry);
  }

  @Override
  public void visit(ChangeFileContentChange c) throws IOException {
    c.revertOn(myRootEntry);

    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    Content content = e.getContent();
    if (content.isAvailable()) {
      f.setBinaryContent(content.getBytes(), -1, e.getTimestamp());
    }
  }

  @Override
  public void visit(RenameChange c) throws IOException, StopVisitingException {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRootEntry);

    f.rename(null, e.getName());
  }

  @Override
  public void visit(MoveChange c) throws IOException {
    Entry e = getAffectedEntry(c, 1);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRootEntry);
    Entry parentEntry = getAffectedEntry(c).getParent();
    VirtualFile parent = myGateway.findVirtualFile(parentEntry.getPath());

    f.move(null, parent);
  }

  @Override
  public void visit(DeleteChange c) throws IOException {
    c.revertOn(myRootEntry);
    Entry e = getAffectedEntry(c);

    revertDeletion(e);
  }

  private void revertDeletion(Entry e) throws IOException {
    VirtualFile parent = myGateway.findVirtualFile(e.getParent().getPath());
    if (e.isDirectory()) {
      parent.createChildDirectory(e, e.getName());
      for (Entry child : e.getChildren()) revertDeletion(child);
    }
    else {
      VirtualFile f = parent.createChildData(e, e.getName());
      f.setBinaryContent(e.getContent().getBytes(), -1, e.getTimestamp());
    }
  }

  protected Entry getAffectedEntry(StructuralChange c) {
    return getAffectedEntry(c, 0);
  }

  private Entry getAffectedEntry(StructuralChange c, int i) {
    return myRootEntry.getEntry(c.getAffectedIdPaths()[i]);
  }
}
