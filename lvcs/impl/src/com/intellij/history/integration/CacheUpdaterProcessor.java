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
        return c.getBytes();
      }

      @Override
      public long getLength() throws IOException {
        return c.getPhysicalLength();
      }
    };
  }
}
