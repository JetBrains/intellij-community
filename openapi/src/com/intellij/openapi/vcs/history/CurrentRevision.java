package com.intellij.openapi.vcs.history;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.Date;


public class CurrentRevision implements VcsFileRevision {
  private final VirtualFile myFile;
  public static final String CURRENT = "Current";
  private final VcsRevisionNumber myRevisionNumber;
  
  public CurrentRevision(VirtualFile file, VcsRevisionNumber revision) {
    myFile = file;
    myRevisionNumber = revision;
  }

  public String getCommitMessage() {
    return "[Current revision]";
  }

  public void loadContent() {
  }

  public Date getRevisionDate() {
    return new Date(myFile.getTimeStamp());
  }

  public byte[] getContent() {
    try {
      Document document = FileDocumentManager.getInstance().getDocument(myFile);
      if (document != null) {
        return document.getText().getBytes(myFile.getCharset().name());
      }
      else {
        return myFile.contentsToByteArray();
      }
    }
    catch (IOException e) {
      Messages.showMessageDialog(e.getLocalizedMessage(), "Could Not Load File Content", Messages.getErrorIcon());
      return null;
    }

  }

  public String getAuthor() {
    return "";
  }

  public VcsRevisionNumber getRevisionNumber() {
    return myRevisionNumber;
  }

}
