package com.intellij.history.integration;

import com.intellij.history.core.ContentFactory;
import com.intellij.history.core.ILocalVcs;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CacheUpdaterProcessor {
  private ILocalVcs myVcs;

  // usage of Set is for quick search
  // usage of LinkedHashSet is for preserving order of files
  // due to performance problems on idea startup caused by hard-drive seeks
  private Set<VirtualFile> myFilesToCreate = new LinkedHashSet<VirtualFile>();
  private Set<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();

  public CacheUpdaterProcessor(ILocalVcs vcs) {
    myVcs = vcs;
  }

  public void addFileToCreate(VirtualFile f) {
    myFilesToCreate.add(f);
  }

  public void addFileToUpdate(VirtualFile f) {
    myFilesToUpdate.add(f);
  }

  public VirtualFile[] queryNeededFiles() {
    List<VirtualFile> result = new ArrayList<VirtualFile>(myFilesToCreate);
    result.addAll(myFilesToUpdate);
    return result.toArray(new VirtualFile[0]);
  }

  public void processFile(FileContent c) {
    VirtualFile f = c.getVirtualFile();
    String path = f.getPath();

    if (myFilesToCreate.contains(f)) {
      // todo: quite of a hack
      // we have to check if file already exists because there is a situation in which
      // there are refresh and update work simultaneously -
      // firstly files created during refresh are collected,
      // when same files are collected during roots change update
      // when roots change updater processes files
      // and at last files collected during refresh are processed.
      // And this causes exception (entry already exists).
      // But it is much better to fix this bug somehow else...
      // but for now we can live with this hack 8)
      if (myVcs.hasEntry(path)) return;
      myVcs.createFile(path, contentFactoryFor(c), f.getTimeStamp(), !f.isWritable());
    }
    else {
      myVcs.changeFileContent(path, contentFactoryFor(c), f.getTimeStamp());
    }
  }

  private ContentFactory contentFactoryFor(final FileContent c) {
    return new ContentFactory() {
      @Override
      public byte[] getBytes() throws IOException {
        return c.getBytes();
      }

      @Override
      public long getLength() throws IOException {
        return c.getPhysicalLength();
      }
    };
  }
}
