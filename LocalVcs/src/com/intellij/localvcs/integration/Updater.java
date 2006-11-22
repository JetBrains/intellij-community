package com.intellij.localvcs.integration;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.LocalVcs;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Updater {
  public static void updateRoots(LocalVcs vcs, VirtualFile... roots) throws IOException {
    // todo should root changes become vesible only after apply?

    for (VirtualFile r : selectRoots(roots)) {
      if (!vcs.hasEntry(r.getPath())) {
        vcs.createRoot(r.getPath());
      }
      updateRoot(vcs, r);
    }

    vcs.apply();
  }

  public static VirtualFile[] selectRoots(VirtualFile... roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile left : roots) {
      boolean isSuitable = true;
      for (VirtualFile right : roots) {
        if (left == right) continue;
        if (left.getPath().startsWith(right.getPath())) isSuitable = false;
      }
      if (isSuitable) result.add(left);
    }
    return result.toArray(new VirtualFile[0]);
  }

  private static void updateRoot(LocalVcs vcs, VirtualFile root) throws IOException {
    // todo test that deleting called first to ensure that sush cases as deleting file 'a'
    // todo and creating dir 'a' are handled correctly

    // todo test that updating is called first
    // todo optimize updating

    createNewFiles(vcs, root);
    updateOutdatedFiles(vcs, root);
    deleteAbsentFiles(vcs, vcs.getEntry(root.getPath()), root);
  }

  private static void updateOutdatedFiles(LocalVcs vcs, VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      if (vcs.hasEntry(f.getPath())) {
        Entry e = vcs.getEntry(f.getPath());

        // todo problem with nested roots
        if (!e.isDirectory() && e.isOutdated(f.getTimeStamp())) {
          vcs.changeFileContent(f.getPath(), new String(f.contentsToByteArray()), f.getTimeStamp());
        }
      }
    }
  }

  private static void createNewFiles(LocalVcs vcs, VirtualFile dir) throws IOException {
    for (VirtualFile f : dir.getChildren()) {
      if (!vcs.hasEntry(f.getPath())) {
        if (f.isDirectory()) {
          vcs.createDirectory(f.getPath(), f.getTimeStamp());
          createNewFiles(vcs, f);
        }
        else {
          vcs.createFile(f.getPath(), new String(f.contentsToByteArray()), f.getTimeStamp());
        }
      }
    }
  }

  private static void deleteAbsentFiles(LocalVcs vcs, Entry entry, VirtualFile dir) {
    for (Entry e : entry.getChildren()) {
      // todo somethig is going wrong with Path abstraction
      // todo move this check to VirtualFile
      VirtualFile f = dir.findChild(e.getPath().getName());

      if (f == null) {
        vcs.delete(e.getPath().getPath());
      }
      else {
        if (e.isDirectory()) {
          deleteAbsentFiles(vcs, e, f);
        }
      }
    }
  }
}
