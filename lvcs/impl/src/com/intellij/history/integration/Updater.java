package com.intellij.history.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

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

  private VirtualFile[] selectParentlessRootsAndSort(List<VirtualFile> roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile r : roots) {
      if (parentIsNotUnderContentRoot(r)) result.add(r);
    }
    sortRoots(result);
    return result.toArray(new VirtualFile[0]);
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
    for (Entry r : myVcs.getRootEntry().getChildren()) {
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

  private boolean hasVcsRoot(VirtualFile f) {
    for (Entry e : myVcs.getRootEntry().getChildren()) {
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
}
