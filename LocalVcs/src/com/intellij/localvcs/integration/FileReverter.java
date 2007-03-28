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
  private Entry myLeftEntry;
  private Entry myRightEntry;

  public static boolean revert(IdeaGateway gw, Label l, Entry left, Entry right) {
    return new FileReverter(gw, l, left, right).revert();
  }

  private FileReverter(IdeaGateway gw, Label l, Entry left, Entry right) {
    myGateway = gw;
    myLabel = l;

    myLeftEntry = left;
    myRightEntry = right;

    myFile = getFile();
  }

  private VirtualFile getFile() {
    if (!hasCurrentVersion()) return null;
    return myGateway.findVirtualFile(myRightEntry.getPath());
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

    if (!hasPreviousVersion()) {
      revertCreation();
    }
    else if (!hasCurrentVersion()) {
      revertDeletion();
    }
    else {
      revertModification();
    }

    return true;
  }

  private boolean userAgreeWithPreconditions() {
    if (!userAllowedModificationOfReadOnlyFiles()) return false;
    if (!userAllowedInterferedFileDeletion()) return false;
    return true;
  }

  private boolean userAllowedModificationOfReadOnlyFiles() {
    if (!hasCurrentVersion()) return true;
    return myGateway.ensureFilesAreWritable(myFile);
  }

  private boolean userAllowedInterferedFileDeletion() {
    if (!hasPreviousVersion()) return true;

    if (findInterferedFile() == null) return true;
    return myGateway.askForProceed("There is file that prevents revertion.\nDo you want to delete that file and proceed?");
  }

  private void revertDeletion() throws IOException {
    // test parent recreation...
    VirtualFile parent = myGateway.findOrCreateDirectory(myLeftEntry.getParent().getPath());

    VirtualFile f = parent.createChildData(null, myLeftEntry.getName());
    f.setBinaryContent(myLeftEntry.getContent().getBytes(), -1, myLeftEntry.getTimestamp());
  }

  private void revertCreation() throws IOException {
    myFile.delete(null);
  }

  private void revertModification() throws IOException {
    removeInterferedFile();
    revertMovement();
    reventRename();
    revertContent();
  }

  private void removeInterferedFile() throws IOException {
    VirtualFile f = findInterferedFile();
    if (f != null) f.delete(null);
  }

  private VirtualFile findInterferedFile() {
    VirtualFile f = myGateway.findVirtualFile(myLeftEntry.getPath());
    if (f == myFile) return null;
    return f;
  }

  private boolean hasPreviousVersion() {
    return myLeftEntry != null;
  }

  private boolean hasCurrentVersion() {
    return myRightEntry != null;
  }

  private void revertMovement() throws IOException {
    String parentPath = myLeftEntry.getParent().getPath();

    if (!Paths.equals(parentPath, myFile.getParent().getPath())) {
      VirtualFile parent = myGateway.findOrCreateDirectory(parentPath);
      myFile.move(null, parent);
    }
  }

  private void reventRename() throws IOException {
    if (!myFile.getName().equals(myLeftEntry.getName())) {
      myFile.rename(null, myLeftEntry.getName());
    }
  }

  private void revertContent() throws IOException {
    if (myFile.getTimeStamp() != myLeftEntry.getTimestamp()) {
      myFile.setBinaryContent(myLeftEntry.getContent().getBytes(), -1, myLeftEntry.getTimestamp());
    }
  }

  private String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myLabel.getTimestamp());
  }
}
