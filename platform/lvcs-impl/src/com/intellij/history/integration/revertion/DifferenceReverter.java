// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.revertion;

import com.intellij.history.core.Content;
import com.intellij.history.core.LocalHistoryFacade;
import com.intellij.history.core.Paths;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

public final class DifferenceReverter extends Reverter {
  private final List<Difference> myDiffs;

  public DifferenceReverter(Project p,
                            LocalHistoryFacade vcs,
                            IdeaGateway gw,
                            List<Difference> diffs,
                            @NotNull Supplier<@NlsContexts.Command String> commandName) {
    super(p, vcs, gw, commandName);
    myDiffs = diffs;
  }

  public DifferenceReverter(Project p, LocalHistoryFacade vcs, IdeaGateway gw, List<Difference> diffs, Revision leftRevision) {
    this(p, vcs, gw, diffs, () -> getRevertCommandName(leftRevision));
  }

  @Override
  protected @NotNull List<VirtualFile> getFilesToClearROStatus() {
    LinkedHashSet<VirtualFile> files = new LinkedHashSet<>();
    for (Difference each : myDiffs) {
      Entry l = each.getLeft();
      Entry r = each.getRight();
      VirtualFile f = l == null ? null : myGateway.findVirtualFile(l.getPath());
      if (f != null) files.add(f);

      f = r == null ? null : myGateway.findVirtualFile(r.getPath());
      if (f != null) files.add(f);
    }
    return new ArrayList<>(files);
  }

  @Override
  protected void doRevert() throws IOException {
    doRevert(true);
  }

  public void doRevert(boolean revertContentChanges) throws IOException {
    Set<String> vetoedFiles = new HashSet<>();

    for (Difference each : ContainerUtil.iterateBackward(myDiffs)) {
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

  private void revertCreation(@NotNull Entry r, @NotNull Set<String> vetoedFiles) throws IOException {
    String path = r.getPath();
    for (String each : vetoedFiles) {
      if (Paths.isParent(path, each)) return;
    }

    VirtualFile f = myGateway.findVirtualFile(path);
    if (f != null) f.delete(this);
  }

  private void revertDeletion(@NotNull Entry l) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(l.getPath(), l.isDirectory());
    if (l.isDirectory()) return;
    setContent(l, f);
  }

  private void revertRename(@NotNull Entry l, @NotNull VirtualFile file) throws IOException {
    String oldName = l.getName();
    if (!oldName.equals(file.getName())) {
      VirtualFile existing = file.getParent().findChild(oldName);
      if (existing != null) {
        existing.delete(this);
      }
      file.rename(this, oldName);
    }
  }

  private static void revertContentChange(@NotNull Entry l, VirtualFile file) throws IOException {
    if (l.isDirectory()) return;
    if (file.getTimeStamp() != l.getTimestamp()) {
      setContent(l, file);
    }
  }

  private static void setContent(@NotNull Entry l, VirtualFile file) throws IOException {
    Content c = l.getContent();
    if (!c.isAvailable()) return;
    file.setBinaryContent(c.getBytes());
  }
}
