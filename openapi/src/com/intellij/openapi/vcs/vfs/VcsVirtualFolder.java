package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.IOException;

public class VcsVirtualFolder extends AbstractVcsVirtualFile {
  private final VirtualFile myChild;
  public VcsVirtualFolder(String name, VirtualFile child, VirtualFileSystem fileSystem) {
    super(name == null ? "" : name, fileSystem);
    myChild = child;
  }

  public VirtualFile[] getChildren() {
    return new VirtualFile[]{myChild};
  }

  public boolean isDirectory() {
    return true;
  }

  public byte[] contentsToByteArray() throws IOException {
    throw new RuntimeException("Should not be called");
  }
}
