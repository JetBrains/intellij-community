package com.intellij.openapi.vcs.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.*;

public abstract class AbstractVcsVirtualFile extends VirtualFile {

  protected final String myName;
  protected final String myPath;
  protected String myRevision;
  private final VirtualFile myParent;
  protected int myModificationStamp = 0;
  private final VirtualFileSystem myFileSystem;

  protected AbstractVcsVirtualFile(String path, VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
    myPath = path;
    File file = new File(myPath);
    myName = file.getName();
    if (!isDirectory())
      myParent = new VcsVirtualFolder(file.getParent(), this, myFileSystem);
    else
      myParent = null;
  }

  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  public String getPathInCvs() {
    return myPath;
  }

  public String getName() {
    return myName;
  }

  public String getPresentableName() {
    if (myRevision == null)
      return myName;
    else
      return myName + " (" + myRevision + ")";
  }

  public void rename(Object requestor, String newName) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return myParent;

  }

  public VirtualFile[] getChildren() {
    return null;
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public void delete(Object requestor) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public InputStream getInputStream() throws IOException {
    return new ByteArrayInputStream(contentsToByteArray());
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new RuntimeException("Could not implement");
  }

  public abstract byte[] contentsToByteArray() throws IOException;

  public char[] contentsToCharArray() throws IOException {
    return new String(contentsToByteArray(), getCharset().name()).toCharArray();
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public long getTimeStamp() {
    return myModificationStamp;
  }

  public long getActualTimeStamp() {
    return myModificationStamp;
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    } catch (IOException e) {
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    if (postRunnable != null)
      postRunnable.run();
  }
}
