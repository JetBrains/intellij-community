package com.intellij.history.integration.ui.views;

import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
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

  private static ContentRevision contentRevisionFrom(DirectoryDifferenceModel m, int i) {
    final Entry e =  m.getEntry(i);
    if (e == null) return null;

    return new ContentRevision() {
      @Nullable
      public String getContent() throws VcsException {
        if (e.isDirectory()) return null;
        return new String(e.getContent().getBytes());
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
