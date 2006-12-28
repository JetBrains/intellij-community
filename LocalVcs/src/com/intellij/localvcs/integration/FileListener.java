package com.intellij.localvcs.integration;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// todo review LocalVcsServiceTests...
public class FileListener extends VirtualFileAdapter {
  private FileFilter myFileFilter;
  private LocalVcs myVcs;

  public FileListener(LocalVcs vcs, FileFilter f) {
    myVcs = vcs;
    myFileFilter = f;
  }

  @Override
  public void fileCreated(VirtualFileEvent e) {
    if (notInteresting(e)) return;
    create(e.getFile());
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    if (notInteresting(e)) return;
    changeFileContent(e.getFile());
  }

  @Override
  public void beforePropertyChange(VirtualFilePropertyEvent e) {
    if (notInteresting(e)) return;
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;
    rename(e.getFile(), (String)e.getNewValue());
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    // todo a bit messy code
    if (isMovedFromOutside(e) && isMovedToOutside(e)) return;

    if (isMovedFromOutside(e)) {
      if (notInteresting(e)) return;
      create(e.getFile());
    }
    else {
      VirtualFile f = new VirtualFileWithParent(e.getOldParent(), e.getFile());
      if (isMovedToOutside(e)) {
        delete(f);
      }
      else {
        move(f, e.getNewParent());
      }
    }
  }

  @Override
  public void beforeFileDeletion(VirtualFileEvent e) {
    if (notInteresting(e)) return;
    delete(e.getFile());
  }

  private boolean notInteresting(VirtualFileEvent e) {
    return !myFileFilter.isFileAllowed(e.getFile());
  }

  private boolean isMovedFromOutside(VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoots(e.getOldParent());
  }

  private boolean isMovedToOutside(final VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoots(e.getNewParent());
  }

  private void create(VirtualFile f) {
    try {
      // todo apply all changes at once
      if (f.isDirectory()) {
        myVcs.createDirectory(f.getPath(), f.getTimeStamp());
        for (VirtualFile ch : f.getChildren()) create(ch);
      }
      else {
        myVcs.createFile(f.getPath(), physicalContentOf(f), f.getTimeStamp());
      }
      myVcs.apply();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void changeFileContent(VirtualFile f) {
    try {
      myVcs.changeFileContent(f.getPath(), physicalContentOf(f), f.getTimeStamp());
      myVcs.apply();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] physicalContentOf(VirtualFile f) throws IOException {
    return LocalFileSystem.getInstance().physicalContentsToByteArray(f);
  }

  private void rename(VirtualFile f, String newName) {
    myVcs.rename(f.getPath(), newName);
    myVcs.apply();
  }

  private void move(VirtualFile file, VirtualFile newParent) {
    myVcs.move(file.getPath(), newParent.getPath());
    myVcs.apply();
  }

  private void delete(VirtualFile f) {
    myVcs.delete(f.getPath());
    myVcs.apply();
  }

  private class VirtualFileWithParent extends VirtualFile {
    private VirtualFile myParent;
    private VirtualFile myChild;

    public VirtualFileWithParent(VirtualFile parent, VirtualFile child) {
      myChild = child;
      myParent = parent;
    }

    public String getPath() {
      return Paths.appended(myParent.getPath(), myChild.getName());
    }

    @NotNull
    @NonNls
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    public boolean isWritable() {
      throw new UnsupportedOperationException();
    }

    public boolean isDirectory() {
      throw new UnsupportedOperationException();
    }

    public boolean isValid() {
      throw new UnsupportedOperationException();
    }

    @Nullable
    public VirtualFile getParent() {
      throw new UnsupportedOperationException();
    }

    public VirtualFile[] getChildren() {
      throw new UnsupportedOperationException();
    }

    public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
      throw new UnsupportedOperationException();
    }

    public byte[] contentsToByteArray() throws IOException {
      throw new UnsupportedOperationException();
    }

    public long getTimeStamp() {
      throw new UnsupportedOperationException();
    }

    public long getLength() {
      throw new UnsupportedOperationException();
    }

    public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
      throw new UnsupportedOperationException();
    }

    public InputStream getInputStream() throws IOException {
      throw new UnsupportedOperationException();
    }
  }

}
