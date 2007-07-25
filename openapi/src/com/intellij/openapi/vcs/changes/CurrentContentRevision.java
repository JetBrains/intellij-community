package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class CurrentContentRevision implements ContentRevision {
  protected FilePath myFile;

  public CurrentContentRevision(final FilePath file) {
    myFile = file;
  }

  @Nullable
  public String getContent() {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) return null;
    final Document doc = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
      public Document compute() {
        return FileDocumentManager.getInstance().getDocument(vFile);
    }});
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

  public static ContentRevision create(FilePath file) {
    if (file.getFileType().isBinary()) {
      return new CurrentBinaryContentRevision(file);
    }
    return new CurrentContentRevision(file);
  }

  @NonNls
  public String toString() {
    return "CurrentContentRevision:" + myFile;
  }
}
