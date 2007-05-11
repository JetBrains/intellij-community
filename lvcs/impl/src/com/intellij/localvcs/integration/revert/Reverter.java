package com.intellij.localvcs.integration.revert;

import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.FormatUtil;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.concurrent.Callable;

public class Reverter {
  private IdeaGateway myGateway;
  private VirtualFile myFile;
  private Revision myRevision;
  private Entry myLeftEntry;
  private Entry myRightEntry;

  public static boolean revert(IdeaGateway gw, Revision r, Entry left, Entry right) {
    return new Reverter(gw, r, left, right).revert();
  }

  private Reverter(IdeaGateway gw, Revision r, Entry left, Entry right) {
    myGateway = gw;
    myRevision = r;

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

    myGateway.saveAllUnsavedDocuments();

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

  private void revertCreation() throws IOException {
    myFile.delete(null);
  }

  private void revertDeletion() throws IOException {
    VirtualFile parent = myGateway.findOrCreateDirectory(getParentOf(myLeftEntry));
    restoreRecursively(parent, myLeftEntry);
  }

  private void restoreRecursively(VirtualFile parent, Entry e) throws IOException {
    if (e.isDirectory()) {
      VirtualFile dir = parent.createChildDirectory(null, getNameOf(e));
      for (Entry child : e.getChildren()) {
        restoreRecursively(dir, child);
      }
    }
    else {
      VirtualFile f = parent.createChildData(null, getNameOf(e));
      f.setBinaryContent(e.getContent().getBytes(), -1, e.getTimestamp());
    }
  }

  private void revertModification() throws IOException {
    removeInterferedFile();
    revertMovement();
    reventRename();

    if (myFile.isDirectory()) {
      revertDirectoryModifications(myFile, myLeftEntry);
    }
    else {
      revertContent();
    }
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

  private void revertMovement() throws IOException {
    String parentPath = getParentOf(myLeftEntry);

    if (!Paths.equals(parentPath, myFile.getParent().getPath())) {
      VirtualFile parent = myGateway.findOrCreateDirectory(parentPath);
      myFile.move(null, parent);
    }
  }

  private void reventRename() throws IOException {
    if (!myFile.getName().equals(getNameOf(myLeftEntry))) {
      myFile.rename(null, getNameOf(myLeftEntry));
    }
  }

  private void revertDirectoryModifications(VirtualFile parentFile, Entry parentEntry) throws IOException {
    if (!parentFile.isDirectory()) {
      if (parentFile.getTimeStamp() != parentEntry.getTimestamp()) {
        parentFile.setBinaryContent(parentEntry.getContent().getBytes(), -1, parentEntry.getTimestamp());
      }
      return;
    }
    for (VirtualFile f : parentFile.getChildren()) {
      Entry e = parentEntry.findChild(f.getName());
      if (e == null) {
        f.delete(null);
      }
      else {
        revertDirectoryModifications(f, e);
      }
    }
    for (Entry e : parentEntry.getChildren()) {
      if (parentFile.findChild(e.getName()) == null) restoreRecursively(parentFile, e);
    }
  }

  private boolean hasPreviousVersion() {
    return myLeftEntry != null;
  }

  private boolean hasCurrentVersion() {
    return myRightEntry != null;
  }


  private void revertContent() throws IOException {
    if (myFile.getTimeStamp() != myLeftEntry.getTimestamp()) {
      myFile.setBinaryContent(myLeftEntry.getContent().getBytes(), -1, myLeftEntry.getTimestamp());
    }
  }

  private String formatCommandName() {
    return "Reverted to " + FormatUtil.formatTimestamp(myRevision.getTimestamp());
  }

  // todo HACK: remove after introducing GhostDirectoryEntry
  private String getParentOf(Entry e) {
    return Paths.getParentOf(e.getPath());
  }

  // todo HACK: remove after introducing GhostDirectoryEntry
  private String getNameOf(Entry e) {
    return Paths.getNameOf(e.getPath());
  }
}
