package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class FileReverter {
  private IdeaGateway myGateway;
  private VirtualFile myFile;
  private Label myLabel;
  private Entry myEntry;

  public static boolean revert(IdeaGateway gw, VirtualFile f, Label l) {
    return new FileReverter(gw, f, l).revert();
  }

  private FileReverter(IdeaGateway gw, VirtualFile f, Label l) {
    myGateway = gw;
    myFile = f;
    myLabel = l;
    myEntry = l.getEntry();
  }

  private boolean revert() {
    return myGateway.performCommandInsideWriteAction(formatCommandName(), new Callable<Boolean>() {
      public Boolean call() throws Exception {
        if (!myGateway.ensureFilesAreWritable(myFile)) return false;
        doRevert();
        return true;
      }
    });
  }

  private void doRevert() throws IOException {
    // what if file already exists
    revertMovement();
    revertChanges();
  }

  private void revertMovement() throws IOException {
    String parentPath = getEntry().getParent().getPath();

    if (!Paths.equals(parentPath, myFile.getParent().getPath())) {
      VirtualFile parent = myGateway.getOrCreateDirectory(parentPath);
      myFile.move(null, parent);
    }
  }

  private void revertChanges() throws IOException {
    if (!myFile.getName().equals(getEntry().getName())) {
      myFile.rename(null, getEntry().getName());
    }
    if (myFile.getTimeStamp() != getEntry().getTimestamp()) {
      myFile.setBinaryContent(getEntry().getContent().getBytes(), -1, getEntry().getTimestamp());
    }
  }

  private String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myLabel.getTimestamp());
  }

  private Entry getEntry() {
    return myEntry;
  }
}
