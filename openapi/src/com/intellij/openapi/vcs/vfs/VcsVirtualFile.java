package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.io.IOException;

/**
 * author: lesya
 */
public class VcsVirtualFile extends AbstractVcsVirtualFile {

  private byte[] myContent;
  private final VcsFileRevision myFileRevision;

  public VcsVirtualFile(String path,
                        VcsFileRevision revision, VirtualFileSystem fileSystem) {
    super(path, fileSystem);
    myFileRevision = revision;
  }

  public VcsVirtualFile(String path,
                        byte[] content,
                        String revision, VirtualFileSystem fileSystem) {
    this(path, null, fileSystem);
    myContent = content;
    setRevision(revision);
  }

  public byte[] contentsToByteArray() throws IOException {
    if (myContent == null) {
      loadContent();
    }
    return myContent;
  }

  private void loadContent() throws IOException {
    final VcsFileSystem vcsFileSystem = ((VcsFileSystem)getFileSystem());

    try {
      myFileRevision.loadContent();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireBeforeContentsChange(this, VcsVirtualFile.this);
        }
      });

      myModificationStamp++;
      setRevision(myFileRevision.getRevisionNumber().asString());
      myContent = myFileRevision.getContent();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireContentsChanged(this, VcsVirtualFile.this, 0);
        }
      });

    }
    catch (VcsException e) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireBeforeFileDeletion(this, VcsVirtualFile.this);
        }
      });
      myContent = new byte[0];
      setRevision("0");

      Messages.showMessageDialog("Could not load content for file " + getPresentableUrl() +
                                 ": " + e.getLocalizedMessage(),
                                 "Could Not Load Content",
                                 Messages.getInformationIcon());

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          vcsFileSystem.fireFileDeleted(this, VcsVirtualFile.this, getName(), false,
                                        getParent());
        }
      });

    }
    catch (ProcessCanceledException ex) {
      myContent = null;
    }

  }

  private void setRevision(String revision) {
    myRevision = revision;
  }


  public boolean isDirectory() {
    return false;
  }

  public String getRevision() {
    if (myRevision == null) {
      try {
        loadContent();
      }
      catch (IOException e) {
        e.printStackTrace(System.err);
      }
    }
    return myRevision;
  }
}
