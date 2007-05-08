package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.Paths;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.vfs.DeprecatedVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class TestVirtualFile extends DeprecatedVirtualFile {
  private String myName;
  private String myContent;
  private long myTimestamp;

  private boolean myIsDirectory;
  private VirtualFile myParent;
  private List<TestVirtualFile> myChildren = new ArrayList<TestVirtualFile>();

  public TestVirtualFile(String name, String content, long timestamp) {
    myName = name;
    myContent = content;
    myTimestamp = timestamp;
    myIsDirectory = false;
  }

  public TestVirtualFile(String name) {
    myName = name;
    myIsDirectory = true;
  }

  public String getName() {
    return myName;
  }

  @Override
  protected boolean nameEquals(String name) {
    return Paths.isCaseSensitive() ? myName.equals(name) : myName.equalsIgnoreCase(name);
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  public String getPath() {
    if (myParent == null) return myName;
    return myParent.getPath() + "/" + myName;
  }

  public long getTimeStamp() {
    return myTimestamp;
  }

  public long getLength() {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getChildren() {
    return myChildren.toArray(new VirtualFile[0]);
  }

  public void addChild(TestVirtualFile f) {
    f.myParent = this;
    myChildren.add(f);
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContent == null ? null : myContent.getBytes();
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return new MockVirtualFileSystem() {
      @Override
      public boolean equals(Object o) {
        return true;
      }
    };
  }

  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VirtualFile getParent() {
    return myParent;
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }

  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }
}
