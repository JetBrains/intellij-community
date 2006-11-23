package com.intellij.localvcs.integration;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestVirtualFile extends VirtualFile {
  private String myName;
  private String myContent;
  private Long myTimestamp;

  private boolean myIsDirectory;
  private VirtualFile myParent;
  private List<TestVirtualFile> myChildren = new ArrayList<TestVirtualFile>();

  public TestVirtualFile(String name, String content, Long timestamp) {
    myName = name;
    myContent = content;
    myTimestamp = timestamp;
    myIsDirectory = false;
  }

  public TestVirtualFile(String name, Long timestamp) {
    myName = name;
    myTimestamp = timestamp;
    myIsDirectory = true;
  }

  public String getName() {
    return myName;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  public String getPath() {
    if (myParent == null) return myName;
    return myParent.getPath() + "/" + myName;
  }

  public long getTimeStamp() {
    return myTimestamp == null ? 0 : myTimestamp;
  }

  public VirtualFile[] getChildren() {
    return myChildren.toArray(new VirtualFile[0]);
  }

  public void addChild(TestVirtualFile f) {
    f.myParent = this;
    myChildren.add(f);
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContent.getBytes();
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }

  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VirtualFile getParent() {
    return null;
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
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
