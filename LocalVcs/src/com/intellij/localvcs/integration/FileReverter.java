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
        return doRevert();
      }
    });
  }

  private boolean doRevert() throws IOException {
    if (!userAgreeWithPreconditions()) return false;

    removeInterferedFile();
    revertMovement();
    reventRename();
    revertContent();

    return true;
  }

  private boolean userAgreeWithPreconditions() {
    if (!myGateway.ensureFilesAreWritable(myFile)) return false;
    if (!userAllowedInterferedFileDeletion()) return false;
    return true;
  }

  private boolean userAllowedInterferedFileDeletion() {
    if (findInterferedFile() == null) return true;
    return myGateway.askForProceed("There is file that prevents revertion.\nDo you want to delete that file and proceed?");
  }

  private void removeInterferedFile() throws IOException {
    VirtualFile f = findInterferedFile();
    if (f != null) f.delete(null);
  }

  private VirtualFile findInterferedFile() {
    VirtualFile f = myGateway.findVirtualFile(myEntry.getPath());
    if (f == myFile) return null;
    return f;
  }

  private void revertMovement() throws IOException {
    String parentPath = myEntry.getParent().getPath();

    if (!Paths.equals(parentPath, myFile.getParent().getPath())) {
      VirtualFile parent = myGateway.findOrCreateDirectory(parentPath);
      myFile.move(null, parent);
    }
  }

  private void reventRename() throws IOException {
    if (!myFile.getName().equals(myEntry.getName())) {
      myFile.rename(null, myEntry.getName());
    }
  }

  private void revertContent() throws IOException {
    if (myFile.getTimeStamp() != myEntry.getTimestamp()) {
      myFile.setBinaryContent(myEntry.getContent().getBytes(), -1, myEntry.getTimestamp());
    }
  }

  private String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myLabel.getTimestamp());
  }
}
