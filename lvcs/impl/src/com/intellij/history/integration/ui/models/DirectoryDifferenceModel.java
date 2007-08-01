package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.ui.views.DirectoryDifference;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectoryDifferenceModel {
  private Difference myDiff;

  public DirectoryDifferenceModel(Difference d) {
    myDiff = d;
  }

  public List<DirectoryDifferenceModel> getChildren() {
    List<DirectoryDifferenceModel> result = new ArrayList<DirectoryDifferenceModel>();
    for (Difference d : myDiff.getChildren()) {
      result.add(new DirectoryDifferenceModel(d));
    }
    return result;
  }

  public boolean isFile() {
    return myDiff.isFile();
  }

  public String getEntryName(int i) {
    Entry e = getEntry(i);
    return e == null ? "" : e.getName();
  }

  public Difference.Kind getDifferenceKind() {
    return myDiff.getKind();
  }

  public Entry getEntry(int i) {
    return i == 0 ? myDiff.getLeft() : myDiff.getRight();
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return new EntireFileDifferenceModel(myDiff.getLeft(), myDiff.getRight());
  }

  public boolean canShowFileDifference() {
    if (!isFile()) return false;
    if (getEntry(0) == null || getEntry(1) == null) return false;
    if (getEntry(0).hasUnavailableContent()) return false;
    if (getEntry(1).hasUnavailableContent()) return false;
    return true;
  }

  public ContentRevision getContentRevision(int i) {
    final Entry e = getEntry(i);
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

  public List<Change> getPlainChanges() {
    List<Change> result = new ArrayList<Change>();
    flatternChanges(result);
    return result;
  }

  private void flatternChanges(List<Change> changes) {
    if (!getDifferenceKind().equals(Difference.Kind.NOT_MODIFIED)) {
      changes.add(new DirectoryDifference(this));
    }
    for (DirectoryDifferenceModel child : getChildren()) {
      child.flatternChanges(changes);
    }
  }
}