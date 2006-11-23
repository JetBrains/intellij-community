package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Updater {
  // todo algorithm could be simplified and improved

  private LocalVcs myVcs;
  private VirtualFile[] myRoots;

  public static void update(LocalVcs vcs, VirtualFile... roots) throws IOException {
    new Updater(vcs, roots).update();
  }

  public Updater(LocalVcs vcs, VirtualFile... roots) {
    myVcs = vcs;
    myRoots = selectNonNestedRoots(roots);
  }

  public static VirtualFile[] selectNonNestedRoots(VirtualFile... roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile left : roots) {
      if (!isNested(left, roots)) result.add(left);
    }
    return result.toArray(new VirtualFile[0]);
  }

  private static boolean isNested(VirtualFile f, VirtualFile... roots) {
    for (VirtualFile another : roots) {
      if (f == another) continue;
      if (f.getPath().startsWith(another.getPath())) return true;
    }
    return false;
  }

  public void update() throws IOException {
    // todo maybe we should delete before updating for optimization 
    updateExistingRoots();
    deleteRemovedRoots();

    myVcs.apply();
  }

  private void updateExistingRoots() throws IOException {
    for (VirtualFile f : myRoots) {
      if (!myVcs.hasEntry(f.getPath())) {
        myVcs.createDirectory(f.getPath(), f.getTimeStamp());
      }
      updateDirectory(f);
    }
  }

  private void deleteRemovedRoots() {
    for (Entry r : myVcs.getRoots()) {
      if (!hasRoot(r)) myVcs.delete(r.getPath());
    }
  }

  private boolean hasRoot(Entry r) {
    for (VirtualFile f : myRoots) {
      if (f.getPath().equals(r.getPath())) return true;
    }
    return false;
  }

  private void updateDirectory(VirtualFile dir) throws IOException {
    if (myVcs.hasEntry(dir.getPath())) {
      deleteAbsentFiles(myVcs.getEntry(dir.getPath()), dir);
    }

    createNewFiles(dir);
    updateOutdatedFiles(dir);
  }

  private void deleteAbsentFiles(Entry entry, VirtualFile dir) {
    for (Entry e : entry.getChildren()) {
      VirtualFile f = dir.findChild(e.getName());
      if (!areOfSameKind(e, f)) {
        myVcs.delete(e.getPath());
      }
      else {
        if (e.isDirectory()) {
          deleteAbsentFiles(e, f);
        }
      }
    }
  }

  private void createNewFiles(VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      Entry e = myVcs.findEntry(f.getPath());
      if (!areOfSameKind(e, f)) {
        if (f.isDirectory()) {
          myVcs.createDirectory(f.getPath(), f.getTimeStamp());
          createNewFiles(f);
        }
        else {
          myVcs.createFile(f.getPath(), new String(f.contentsToByteArray()), f.getTimeStamp());
        }
      }
    }
  }

  private void updateOutdatedFiles(VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      Entry e = myVcs.findEntry(f.getPath());
      if (areOfSameKind(e, f)) {
        // todo we should update directory and root timestamps too
        if (!e.isDirectory() && e.isOutdated(f.getTimeStamp())) {
          myVcs.changeFileContent(f.getPath(), new String(f.contentsToByteArray()), f.getTimeStamp());
        }
      }
    }
  }

  private boolean areOfSameKind(Entry e, VirtualFile f) {
    if (e == null || f == null) return false;
    return e.isDirectory() == f.isDirectory();
  }
}
