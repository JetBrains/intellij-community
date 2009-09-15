package com.intellij.history.integration.revertion;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.FormatUtil;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.utils.Reversed;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DifferenceReverter extends Reverter {
  private final IdeaGateway myGateway;
  private final List<Difference> myDiffs;
  private final Revision myLeftRevision;

  public DifferenceReverter(LocalVcs vcs, IdeaGateway gw, List<Difference> diffs, Revision leftRevision) {
    super(vcs, gw);
    myGateway = gw;
    myDiffs = diffs;
    myLeftRevision = leftRevision;
  }

  @Override
  protected String formatCommandName() {
    String date = FormatUtil.formatTimestamp(myLeftRevision.getTimestamp());
    return LocalHistoryBundle.message("system.label.revert.to.date", date);
  }

  @Override
  protected List<VirtualFile> getFilesToClearROStatus() throws IOException {
    LinkedHashSet<VirtualFile> files = new LinkedHashSet<VirtualFile>();
    for (Difference each : myDiffs) {
      Entry l = each.getLeft();
      Entry r = each.getRight();
      VirtualFile f = l == null ? null : myGateway.findVirtualFile(l.getPath());
      if (f != null) files.add(f);

      f = r == null ? null : myGateway.findVirtualFile(r.getPath());
      if (f != null) files.add(f);
    }
    return new ArrayList<VirtualFile>(files);
  }

  @Override
  protected void doRevert() throws IOException {
    doRevert(true);
  }

  public void doRevert(boolean revertContentChanges) throws IOException {
    Set<String> vetoedFiles = new THashSet<String>();

    for (Difference each : Reversed.list(myDiffs)) {
      Entry l = each.getLeft();
      Entry r = each.getRight();

      if (l == null) {
        revertCreation(r, vetoedFiles);
        continue;
      }

      vetoedFiles.add(l.getPath());
      if (r == null) {
        revertDeletion(l);
        continue;
      }

      VirtualFile file = myGateway.findOrCreateFileSafely(r.getPath(), r.isDirectory());
      revertRename(l, file);
      if (revertContentChanges) revertContentChange(l, file);
    }
  }

  private void revertCreation(Entry r, Set<String> vetoedFiles) throws IOException {
    String path = r.getPath();
    for (String each : vetoedFiles) {
      if (Paths.isParent(path, each)) return;
    }
    
    VirtualFile f = myGateway.findVirtualFile(path);
    if (f != null) f.delete(this);
  }

  private void revertDeletion(Entry l) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(l.getPath(), l.isDirectory());
    if (l.isDirectory()) return;
    setContent(l, f);
  }

  private void revertRename(Entry l, VirtualFile file) throws IOException {
    String oldName = getNameOf(l);
    if (!oldName.equals(file.getName())) {
      VirtualFile existing = file.getParent().findChild(oldName);
      if (existing != null) {
        existing.delete(this);
      }
      file.rename(this, oldName);
    }
  }

  private void revertContentChange(Entry l, VirtualFile file) throws IOException {
    if (l.isDirectory()) return;
    if (file.getTimeStamp() != l.getTimestamp()) {
      setContent(l, file);
    }
  }

  private void setContent(Entry l, VirtualFile file) throws IOException {
    Content c = l.getContent();
    if (!c.isAvailable()) return;
    file.setBinaryContent(c.getBytes(), -1, l.getTimestamp());
  }

  // todo refactor to a base class

  // todo HACK: remove after introducing GhostDirectoryEntry
  private String getParentOf(Entry e) {
    // roots do not have parents - only paths
    return Paths.getParentOf(e.getPath());
  }

  // todo HACK: remove after introducing GhostDirectoryEntry
  private String getNameOf(Entry e) {
    // name for roots may be the path
    return Paths.getNameOf(e.getPath());
  }
}
