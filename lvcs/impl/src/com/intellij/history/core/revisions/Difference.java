package com.intellij.history.core.revisions;

import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class Difference {
  private boolean myIsFile;
  private Entry myLeft;
  private Entry myRight;

  public Difference(boolean isFile, Entry left, Entry right) {
    myIsFile = isFile;
    myLeft = left;
    myRight = right;
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

  public ContentRevision getLeftContentRevision() {
    return createContentRevision(getLeft());
  }

  public ContentRevision getRightContentRevision() {
    return createContentRevision(getRight());
  }

  private ContentRevision createContentRevision(final Entry e) {
    if (e == null) return null;

    return new ContentRevision() {
      @Nullable
      public String getContent() throws VcsException {
        if (e.isDirectory()) return null;
        return e.getContent().getString();
      }

      @NotNull
      public FilePath getFile() {
        return new FilePathImpl(new File(e.getPath()), e.isDirectory());
      }

      @NotNull
      public VcsRevisionNumber getRevisionNumber() {
        return VcsRevisionNumber.NULL;
      }
    };
  }
}
