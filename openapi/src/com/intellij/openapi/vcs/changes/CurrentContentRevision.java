package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class CurrentContentRevision implements ContentRevision {
  private FilePath myFile;

  public CurrentContentRevision(final FilePath file) {
    myFile = file;
  }

  @Nullable
  public String getContent() {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) return null;
    final Document doc = FileDocumentManager.getInstance().getDocument(vFile);
    return doc.getText();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    final VirtualFile vFile = myFile.getVirtualFile();
    if (vFile == null || !vFile.isValid()) return null;
    return vFile;
  }

  @NotNull
  public FilePath getFile() {
    return myFile;
  }

  @NotNull
  public VcsRevisionNumber getRevisionNumber() {
    return VcsRevisionNumber.NULL;
  }
}
