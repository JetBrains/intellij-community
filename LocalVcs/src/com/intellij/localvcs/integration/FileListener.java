package com.intellij.localvcs.integration;

import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileListener extends VirtualFileAdapter {
  private FileFilter myFileFilter;
  private ILocalVcs myVcs;
  private LocalFileSystem myFileSystem;

  public FileListener(ILocalVcs vcs, FileFilter f, LocalFileSystem fs) {
    myVcs = vcs;
    myFileFilter = f;
    myFileSystem = fs;
  }

  @Override
  public void fileCreated(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    create(e.getFile());
  }

  @Override
  public void contentsChanged(VirtualFileEvent e) {
    if (notAllowedOrNotUnderContentRoot(e)) return;
    changeFileContent(e.getFile());
  }

  @Override
  public void beforePropertyChange(VirtualFilePropertyEvent e) {
    if (!e.getPropertyName().equals(VirtualFile.PROP_NAME)) return;

    if (!myFileFilter.isUnderContentRoot(e.getFile())) return;

    VirtualFile newFile = new RenamedVirtualFile(e.getFile(), (String)e.getNewValue());

    // todo try make it more clear... and refactor
    if (!myFileFilter.isAllowed(newFile)) {
      if (myFileFilter.isAllowed(e.getFile())) delete(e.getFile());
      return;
    }

    if (!myFileFilter.isAllowed(e.getFile())) {
      create(newFile);
      return;
    }

    rename(e.getFile(), (String)e.getNewValue());
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent e) {
    // todo a bit messy code
    if (isMovedFromOutside(e) && isMovedToOutside(e)) return;

    if (isMovedFromOutside(e)) {
      if (notAllowedOrNotUnderContentRoot(e)) return;
      create(e.getFile());
    }
    else {
      VirtualFile f = new ReparentedVirtualFile(e.getOldParent(), e.getFile());
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
    if (!myFileFilter.isUnderContentRoot(e.getFile())) return;
    if (!myVcs.hasEntry(e.getFile().getPath())) return;
    delete(e.getFile());
  }

  private boolean notAllowedOrNotUnderContentRoot(VirtualFileEvent e) {
    return !myFileFilter.isAllowedAndUnderContentRoot(e.getFile());
  }

  private boolean isMovedFromOutside(VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoot(e.getOldParent());
  }

  private boolean isMovedToOutside(VirtualFileMoveEvent e) {
    return !myFileFilter.isUnderContentRoot(e.getNewParent());
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
    return myFileSystem.physicalContentsToByteArray(f);
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

  private class ReparentedVirtualFile extends NullVirtualFile {
    private VirtualFile myParent;
    private VirtualFile myChild;

    public ReparentedVirtualFile(VirtualFile newParent, VirtualFile child) {
      myChild = child;
      myParent = newParent;
    }

    public String getPath() {
      return Paths.appended(myParent.getPath(), myChild.getName());
    }
  }

  private class RenamedVirtualFile extends NullVirtualFile {
    private VirtualFile myFile;
    private String myNewName;

    public RenamedVirtualFile(VirtualFile f, String newName) {
      myFile = f;
      myNewName = newName;
    }

    @Override
    @NotNull
    @NonNls
    public String getName() {
      return myNewName;
    }

    public String getPath() {
      return Paths.renamed(myFile.getPath(), myNewName);
    }

    public long getTimeStamp() {
      return myFile.getTimeStamp();
    }

    public boolean isDirectory() {
      return myFile.isDirectory();
    }

    public byte[] contentsToByteArray() throws IOException {
      return myFile.contentsToByteArray();
    }

    public long getLength() {
      return myFile.getLength();
    }
  }

  private class NullVirtualFile extends VirtualFile {
    @NotNull
    @NonNls
    public String getName() {
      throw new UnsupportedOperationException();
    }

    @NotNull
    public VirtualFileSystem getFileSystem() {
      throw new UnsupportedOperationException();
    }

    public String getPath() {
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
