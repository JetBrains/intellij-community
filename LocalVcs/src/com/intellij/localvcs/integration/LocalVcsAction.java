package com.intellij.localvcs.integration;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;

// todo add LocalVcs.startAction for bulk updates
public class LocalVcsAction implements ILocalVcsAction {
  public static LocalVcsAction NULL = new Null(); // todo try to get rid of this

  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private String myLabel;

  public LocalVcsAction(ILocalVcs vcs, IdeaGateway gw, String label) {
    myVcs = vcs;
    myGateway = gw;
    myLabel = label;
  }

  public void start() {
    myVcs.beginChangeSet();
    applyUnsavedDocuments();
  }

  public void finish() {
    applyUnsavedDocuments();
    myVcs.endChangeSet(myLabel);
  }

  private void applyUnsavedDocuments() {
    for (Document d : myGateway.getUnsavedDocuments()) {
      VirtualFile f = myGateway.getDocumentFile(d);
      if (!myGateway.getFileFilter().isAllowedAndUnderContentRoot(f)) continue;
      myVcs.changeFileContent(f.getPath(), d.getText().getBytes(), f.getTimeStamp());
    }
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
