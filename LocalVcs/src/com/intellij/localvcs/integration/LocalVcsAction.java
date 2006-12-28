package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

public class LocalVcsAction {
  public static LocalVcsAction NULL = new Null(); // todo try to get rid of this

  private LocalVcs myVcs;
  private FileDocumentManager myDocumentManager;
  private FileFilter myFilter;

  public LocalVcsAction(LocalVcs vcs, FileDocumentManager dm, FileFilter f) {
    myVcs = vcs;
    myDocumentManager = dm;
    myFilter = f;
  }

  public void start() {
    applyUnsavedDocuments();
  }

  public void finish() {
    applyUnsavedDocuments();
  }

  private void applyUnsavedDocuments() {
    for (Document d : myDocumentManager.getUnsavedDocuments()) {
      VirtualFile f = myDocumentManager.getFile(d);
      // todo charset
      // todo move filtering to some kind of decorator or adaptor...
      if (!myFilter.isFileAllowed(f)) continue;
      myVcs.changeFileContent(f.getPath(), d.getText().getBytes(), f.getTimeStamp());
    }
    myVcs.apply();
  }

  private static class Null extends LocalVcsAction {
    public Null() {
      super(null, null, null);
    }

    @Override
    public void start() {
    }

    @Override
    public void finish() {
    }
  }
}
