// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.history.core.revisions;

import com.intellij.history.core.Paths;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Difference {
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

  public @Nullable Entry getLeft() {
    return myLeft;
  }

  public @Nullable Entry getRight() {
    return myRight;
  }

  public @Nullable FilePath getFilePath() {
    if (myRight != null) return getFilePath(myRight);
    if (myLeft != null) return getFilePath(myLeft);
    return null;
  }

  public ContentRevision getLeftContentRevision(IdeaGateway gw) {
    return createContentRevision(getLeft(), gw);
  }

  public ContentRevision getRightContentRevision(IdeaGateway gw) {
    Entry entry = getRight();
    if (myRightContentCurrent && entry != null) {
      VirtualFile file = gw.findVirtualFile(entry.getPath());
      if (file != null) {
        return new CurrentContentRevision(Paths.createDvcsFilePath(file));
      }
    }
    return createContentRevision(entry, gw);
  }

  private static ContentRevision createContentRevision(@Nullable Entry e, final IdeaGateway gw) {
    if (e == null) return null;

    return new ByteBackedContentRevision() {
      @Override
      public @Nullable String getContent() {
        if (e.isDirectory()) return null;
        return e.getContent().getString(e, gw);
      }

      @Override
      public byte @Nullable [] getContentAsBytes() {
        if (e.isDirectory()) return null;
        return e.getContent().getBytes();
      }

      @Override
      public @NotNull FilePath getFile() {
        return getFilePath(e);
      }

      @Override
      public @NotNull VcsRevisionNumber getRevisionNumber() {
        return VcsRevisionNumber.NULL;
      }
    };
  }

  private static @NotNull FilePath getFilePath(@NotNull Entry entry) {
    return Paths.createDvcsFilePath(entry.getPath(), entry.isDirectory());
  }
}
