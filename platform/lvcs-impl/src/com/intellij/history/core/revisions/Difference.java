// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core.revisions;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Difference {
  private final boolean myIsFile;
  private final Entry myLeft;
  private final Entry myRight;
  private final boolean myRightContentCurrent;

  public Difference(boolean isFile, Entry left, Entry right) {
    this(isFile, left, right, false);
  }

  public Difference(boolean isFile, @Nullable Entry left, @Nullable Entry right, boolean isRightContentCurrent) {
    myIsFile = isFile;
    myLeft = left;
    myRight = right;
    myRightContentCurrent = isRightContentCurrent;
  }

  public boolean isFile() {
    return myIsFile;
  }

  public Entry getLeft() {
    return myLeft;
  }

  public Entry getRight() {
    return myRight;
  }

  public ContentRevision getLeftContentRevision(IdeaGateway gw) {
    return createContentRevision(getLeft(), gw);
  }

  public ContentRevision getRightContentRevision(IdeaGateway gw) {
    Entry entry = getRight();
    if (myRightContentCurrent && entry != null) {
      VirtualFile file = gw.findVirtualFile(entry.getPath());
      if (file != null) return new CurrentContentRevision(VcsUtil.getFilePath(file));
    }
    return createContentRevision(entry, gw);
  }

  private static ContentRevision createContentRevision(@Nullable Entry e, final IdeaGateway gw) {
    if (e == null) return null;

    return new ByteBackedContentRevision() {
      @Override
      @Nullable
      public String getContent() throws VcsException {
        if (e.isDirectory()) return null;
        return e.getContent().getString(e, gw);
      }

      @Override
      public byte @Nullable [] getContentAsBytes() throws VcsException {
        if (e.isDirectory()) return null;
        return e.getContent().getBytes();
      }

      @Override
      @NotNull
      public FilePath getFile() {
        return VcsUtil.getFilePath(e.getPath(), e.isDirectory());
      }

      @Override
      @NotNull
      public VcsRevisionNumber getRevisionNumber() {
        return VcsRevisionNumber.NULL;
      }
    };
  }
}
