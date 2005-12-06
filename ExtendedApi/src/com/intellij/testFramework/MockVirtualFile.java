package com.intellij.testFramework;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MockVirtualFile extends VirtualFile {
  public String myContent = "";
  protected String myName = "";
  public long myModStamp = LocalTimeCounter.currentTime();
  protected long myTimeStamp = System.currentTimeMillis();
  protected long myActualTimeStamp = myTimeStamp;
  private boolean myIsWritable = true;
  private VirtualFileListener myListener = null;

  public MockVirtualFile() {
  }

  public MockVirtualFile(String name) {
    myName = name;
  }

  public MockVirtualFile(String name, String content) {
    myName = name;
    myContent = content;
  }

  public void setListener(VirtualFileListener listener) {
    myListener = listener;
  }

  public VirtualFileSystem getFileSystem() {
    return null;
  }

  public String getPath() {
    return "/" + getName();
  }

  public String getName() {
    return myName;
  }

  public boolean isWritable() {
    return myIsWritable;
  }

  public boolean isDirectory() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public VirtualFile getParent() {
    return null;
  }

  public VirtualFile[] getChildren() {
    return VirtualFile.EMPTY_ARRAY;
  }

  public InputStream getInputStream() throws IOException {
    return null;
  }

  public OutputStream getOutputStream(Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
    return new ByteArrayOutputStream() {
      public void close() throws IOException {
        myModStamp = newModificationStamp;
        myContent = toString();
      }
    };
  }

  public byte[] contentsToByteArray() throws IOException {
    return myContent.getBytes();
  }

  public long getModificationStamp() {
    return myModStamp;
  }

  public long getTimeStamp() {
    return myTimeStamp;
  }

  public void setActualTimeStamp(long actualTimeStamp) {
    myActualTimeStamp = actualTimeStamp;
  }

  public long getActualTimeStamp() {
    return myActualTimeStamp;
  }

  public long getLength() {
    try {
      return contentsToByteArray().length;
    }
    catch (IOException e) {
      e.printStackTrace();
      Assert.assertTrue(false);
      return 0;
    }
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }

  public void setContent(Object requestor, String content, boolean fireEvent) {
    long oldStamp = myModStamp;
    myContent = content;
    if (fireEvent) {
      myModStamp = LocalTimeCounter.currentTime();
      myListener.contentsChanged(new VirtualFileEvent(requestor, this, null, oldStamp, myModStamp));
    }
  }

  public VirtualFile self() {
    return this;
  }

  public void setWritable(boolean b) {
    myIsWritable = b;
  }
}
