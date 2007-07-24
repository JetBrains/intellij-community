package com.intellij.history.integration.revertion;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.Paths;
import com.intellij.history.core.changes.*;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChangeRevertionVisitor extends ChangeVisitor {
  private IdeaGateway myGateway;
  private Map<VirtualFile, ContentToApply> myContentsToApply = new HashMap<VirtualFile, ContentToApply>();

  public ChangeRevertionVisitor(IdeaGateway gw) {
    myGateway = gw;
  }

  @Override
  public void visit(CreateEntryChange c) throws IOException {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    unregisterContentToApply(f);
    f.delete(this);

    c.revertOn(myRoot);
  }

  @Override
  public void visit(ChangeFileContentChange c) {
    c.revertOn(myRoot);

    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());
    registerContentToApply(f, e);
  }

  @Override
  public void visit(RenameChange c) throws IOException, StopVisitingException {
    Entry e = getAffectedEntry(c);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRoot);

    f.rename(this, e.getName());
  }

  @Override
  public void visit(MoveChange c) throws IOException {
    Entry e = getAffectedEntry(c, 1);
    VirtualFile f = myGateway.findVirtualFile(e.getPath());

    c.revertOn(myRoot);
    String parentPath = getParentPath(getAffectedEntry(c));
    VirtualFile parent = myGateway.findVirtualFile(parentPath);

    f.move(this, parent);
  }

  @Override
  public void visit(DeleteChange c) throws IOException {
    c.revertOn(myRoot);
    Entry e = getAffectedEntry(c);

    revertDeletion(e);
  }

  private void revertDeletion(Entry e) throws IOException {
    VirtualFile parent = myGateway.findVirtualFile(getParentPath(e));
    if (e.isDirectory()) {
      parent.createChildDirectory(e, getName(e));
      for (Entry child : e.getChildren()) revertDeletion(child);
    }
    else {
      VirtualFile f = parent.createChildData(e, getName(e));
      registerContentToApply(f, e);
    }
  }

  private void registerContentToApply(VirtualFile f, Entry e) {
    myContentsToApply.put(f, new ContentToApply(e));
  }

  private void unregisterContentToApply(VirtualFile fileOrDir) {
    List<VirtualFile> registered = new ArrayList<VirtualFile>(myContentsToApply.keySet());
    for (VirtualFile f : registered) {
      if (VfsUtil.isAncestor(fileOrDir, f, false)) {
        myContentsToApply.remove(f);
      }
    }
  }

  @Override
  public void finished() throws IOException {
    for (Map.Entry<VirtualFile, ContentToApply> e : myContentsToApply.entrySet()) {
      VirtualFile f = e.getKey();
      ContentToApply c = e.getValue();
      c.applyTo(f);
    }
  }

  protected Entry getAffectedEntry(StructuralChange c) {
    return getAffectedEntry(c, 0);
  }

  private Entry getAffectedEntry(StructuralChange c, int i) {
    return myRoot.getEntry(c.getAffectedIdPaths()[i]);
  }

  private String getParentPath(Entry e) {
    return Paths.getParentOf(e.getPath());
  }

  private String getName(Entry e) {
    return Paths.getNameOf(e.getPath());
  }

  private static class ContentToApply {
    private Content myContent;
    private long myTimestamp;

    public ContentToApply(Entry e) {
      myContent = e.getContent();
      myTimestamp = e.getTimestamp();
    }

    public void applyTo(VirtualFile f) throws IOException {
      f.setBinaryContent(myContent.getBytes(), -1, myTimestamp);
    }
  }
}
