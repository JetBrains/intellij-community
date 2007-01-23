package com.intellij.localvcs.integration;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

// todo add LocalVcs.startAction for bulk updates
public class LocalVcsAction implements ILocalVcsAction {
  public static LocalVcsAction NULL = new Null(); // todo try to get rid of this

  private ILocalVcs myVcs;
  private FileDocumentManager myDocumentManager;
  private FileFilter myFilter;
  private String myLabel;

  public LocalVcsAction(ILocalVcs vcs, FileDocumentManager dm, FileFilter f, String label) {
    myVcs = vcs;
    myDocumentManager = dm;
    myFilter = f;
    myLabel = label;
  }

  public void start() {
    applyUnsavedDocuments();
  }

  public void finish() {
    applyUnsavedDocuments();
    myVcs.putLabel(myLabel);
  }

  private void applyUnsavedDocuments() {
    for (Document d : myDocumentManager.getUnsavedDocuments()) {
      VirtualFile f = myDocumentManager.getFile(d);
      // todo charset
      // todo move filtering to some kind of decorator or adaptor...

      if (!myFilter.isAllowedAndUnderContentRoot(f)) continue;
      myVcs.changeFileContent(f.getPath(), d.getText().getBytes(), f.getTimeStamp());
    }
    myVcs.apply();
  }

  private static class Null extends LocalVcsAction {
    public Null() {
      super(null, null, null, null);
    }

    @Override
    public void start() {
    }

    @Override
    public void finish() {
    }
  }
}
