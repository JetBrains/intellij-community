package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DirectoryDifference extends Change {
  private DirectoryDifferenceModel myModel;

  public DirectoryDifference(DirectoryDifferenceModel m) {
    super(leftContentRevisionFrom(m), rightContentRevisionFrom(m));
    myModel = m;
  }

  public DirectoryDifferenceModel getModel() {
    return myModel;
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return myModel.getFileDifferenceModel();
  }

  public boolean canShowFileDifference() {
    return myModel.canShowFileDifference();
  }

  private static ContentRevision leftContentRevisionFrom(DirectoryDifferenceModel m) {
    return contentRevisionFrom(m, 0);
  }

  private static ContentRevision rightContentRevisionFrom(DirectoryDifferenceModel m) {
    return contentRevisionFrom(m, 1);
  }

  private static ContentRevision contentRevisionFrom(final DirectoryDifferenceModel m, final int i) {
    if (m.getEntry(i) == null) return null;
    return new ContentRevision() {
      @Nullable
      public String getContent() throws VcsException {
        if (!m.isFile()) return null;
        return new String(getEntry().getContent().getBytes());
      }

      @NotNull
      public FilePath getFile() {
        return new FilePathImpl(new File(getEntry().getPath()), getEntry().isDirectory());
      }

      @NotNull
      public VcsRevisionNumber getRevisionNumber() {
        return VcsRevisionNumber.NULL;
      }

      private Entry getEntry() {
        return m.getEntry(i);
      }
    };
  }
}
