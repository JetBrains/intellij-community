package com.intellij.history.integration;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.tree.Entry;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.*;

public class Updater implements CacheUpdater {
  private ILocalVcs myVcs;
  private IdeaGateway myGateway;
  private VirtualFile[] myVfsRoots;

  // usage of Set is for quick search
  // usage of LinkedHashSet is for preserving order of files
  // due to performance problems on idea startup caused by hard-drive seeks
  private Set<VirtualFile> myFilesToCreate = new LinkedHashSet<VirtualFile>();
  private Set<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();

  public Updater(ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myGateway = gw;
    myVfsRoots = selectParentlessRootsAndSort(gw.getContentRoots());
  }

  protected VirtualFile[] selectParentlessRootsAndSort(List<VirtualFile> roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile r : roots) {
      if (parentIsNotUnderContentRoot(r)) result.add(r);
    }
    removeDupplicates(result);
    sortRoots(result);
    return result.toArray(new VirtualFile[0]);
  }

  private void removeDupplicates(List<VirtualFile> roots) {
    ContainerUtil.removeDuplicates(roots);
  }

  private void sortRoots(List<VirtualFile> roots) {
    Collections.sort(roots, new Comparator<VirtualFile>() {
      public int compare(VirtualFile a, VirtualFile b) {
        boolean ancestor = VfsUtil.isAncestor(a, b, false);
        return ancestor ? -1 : 1;
      }
    });
  }

  private boolean parentIsNotUnderContentRoot(VirtualFile r) {
    VirtualFile p = r.getParent();
    return p == null || !myGateway.getFileFilter().isUnderContentRoot(p);
  }

  public VirtualFile[] queryNeededFiles() {
    myVcs.beginChangeSet();

    deleteObsoleteRoots();
    createAndUpdateRoots();

    List<VirtualFile> result = new ArrayList<VirtualFile>(myFilesToCreate);
    result.addAll(myFilesToUpdate);
    return result.toArray(new VirtualFile[0]);
  }

  public void processFile(FileContent c) {
    VirtualFile f = c.getVirtualFile();
    if (myFilesToCreate.contains(f)) {
      myVcs.createFile(f.getPath(), contentFactoryFor(c), f.getTimeStamp());
    }
    else {
      // todo catching IDEADEV-18801 bug (asking invalid file for timestamp)
      // the only possibilities are:
      // file was removed between queryNeededFiles and processFile
      // processFile was called with file which was not into created nor updated files
      assert myFilesToUpdate.contains(f);
      myVcs.changeFileContent(f.getPath(), contentFactoryFor(c), f.getTimeStamp());
    }
  }

  private ContentFactory contentFactoryFor(final FileContent c) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() throws IOException {
        return c.getPhysicalBytes();
      }

      @Override
      public long getLength() throws IOException {
        return c.getPhysicalLength();
      }
    };
  }

  public void updatingDone() {
    myVcs.endChangeSet(null);
  }

  public void canceled() {
    // TODO ? Save changes processed so far?
  }

  private void deleteObsoleteRoots() {
    List<Entry> obsolete = new ArrayList<Entry>();
    for (Entry r : myVcs.getRoots()) {
      if (!hasVfsRoot(r)) obsolete.add(r);
    }
    for (Entry e : obsolete) myVcs.delete(e.getPath());
  }

  private boolean hasVfsRoot(Entry e) {
    for (VirtualFile f : myVfsRoots) {
      if (e.pathEquals(f.getPath())) return true;
    }
    return false;
  }

  private void createAndUpdateRoots() {
    for (VirtualFile r : myVfsRoots) {
      if (!hasVcsRoot(r)) {
        createRecursively(r);
      }
      else {
        updateRecursively(myVcs.getEntry(r.getPath()), r);
      }
    }
  }

  private boolean hasVcsRoot(VirtualFile f) {
    for (Entry e : myVcs.getRoots()) {
      if (e.pathEquals(f.getPath())) return true;
    }
    return false;
  }

  private void updateRecursively(Entry entry, VirtualFile dir) {
    for (VirtualFile f : dir.getChildren()) {
      if (notAllowed(f)) continue;

      Entry e = entry.findChild(f.getName());
      if (e == null) {
        createRecursively(f);
      }
      else if (notTheSameKind(e, f)) {
        myVcs.delete(e.getPath());
        createRecursively(f);
      }
      else {
        if (!e.getName().equals(f.getName())) {
          myVcs.rename(e.getPath(), f.getName());
        }
        if (f.isDirectory()) {
          updateRecursively(e, f);
        }
        else {
          if (e.isOutdated(f.getTimeStamp())) {
            myFilesToUpdate.add(f);
          }
        }
      }
    }
    deleteObsoleteFiles(entry, dir);
  }

  private boolean notTheSameKind(Entry e, VirtualFile f) {
    return e.isDirectory() != f.isDirectory();
  }

  private void deleteObsoleteFiles(Entry entry, VirtualFile dir) {
    List<Entry> obsolete = new ArrayList<Entry>();
    for (Entry e : entry.getChildren()) {
      VirtualFile f = dir.findChild(e.getName());
      if (f == null || notAllowed(f)) {
        obsolete.add(e);
      }
    }
    for (Entry e : obsolete) myVcs.delete(e.getPath());
  }

  private void createRecursively(VirtualFile fileOrDir) {
    if (notAllowed(fileOrDir)) return;

    if (fileOrDir.isDirectory()) {
      // todo catching IDEADEV-18728 bug
      assertNotExists(fileOrDir);

      myVcs.createDirectory(fileOrDir.getPath());
      for (VirtualFile f : fileOrDir.getChildren()) {
        createRecursively(f);
      }
    }
    else {
      myFilesToCreate.add(fileOrDir);
    }
  }

  private boolean notAllowed(VirtualFile f) {
    return !myGateway.getFileFilter().isAllowedAndUnderContentRoot(f);
  }

  private void assertNotExists(VirtualFile f) {
    Entry e = myVcs.findEntry(f.getPath());
    if (e == null) return;

    StringBuilder b = new StringBuilder();

    b.append("already exists!!\n");
    b.append("file: " + f + "\n");
    b.append("file.name: " + f.getName() + "\n");
    b.append("file.parent: " + f.getParent() + "\n");
    if (f.getParent() != null) b.append("file.parent.name: " + f.getParent().getName() + "\n");
    b.append("entry: " + e + "\n");
    b.append("entry.parent: " + e.getParent() + "\n");
    b.append("has vcs root: " + hasVcsRoot(f) + "\n");
    b.append("has vfs root: " + hasVfsRoot(e) + "\n");
    b.append("is file allowed: " + !notAllowed(f) + "\n");
    if (f.getParent() != null) b.append("is file parent allowed: " + !notAllowed(f.getParent()) + "\n");
    log(b, myVfsRoots);
    log(b, myVcs.getRoots());
    throw new RuntimeException(b.toString());
  }

  private void log(StringBuilder b, VirtualFile[] roots) {
    b.append("vfs roots:\n");
    for (VirtualFile r : roots) {
      b.append("-" + r + "\n");
    }
  }

  private void log(StringBuilder b, List<Entry> roots) {
    b.append("vcs roots:\n");
    for (Entry r : roots) {
      b.append("-" + r + "\n");
    }
  }
}
