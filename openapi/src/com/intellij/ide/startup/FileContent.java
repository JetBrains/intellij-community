package com.intellij.ide.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;

import java.io.IOException;

/**
 * @author max
 */
public class FileContent implements UserDataHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileContent");
  private static final byte[] EMPTY_CONTENT = new byte[0];

  private VirtualFile myVirtualFile;
  private byte[] myCachedBytes;
  private THashMap myUserMap = null;
  private boolean myGetBytesCalled = false;
  private long myLength = -1;

  public FileContent(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public byte[] getBytes() throws IOException {
    myGetBytesCalled = true;
    if (myCachedBytes == null) {
      myCachedBytes = myVirtualFile.contentsToByteArray();
    }

    return myCachedBytes;
  }

  public byte[] getPhysicalBytes() throws IOException {
    if (myGetBytesCalled) {
      LOG.error("getPhysicalBytes() called after getBytes() for " + myVirtualFile);
    }

    if (myCachedBytes == null) {
      myCachedBytes = LocalFileSystem.getInstance().physicalContentsToByteArray(myVirtualFile);
    }

    return myCachedBytes;
  }

  public void setEmptyContent() {
    myCachedBytes = EMPTY_CONTENT;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public <T> T getUserData(Key<T> key){
    synchronized(this){
      if (myUserMap == null) return null;
      return (T)myUserMap.get(key);
    }
  }

  public <T> void putUserData(Key<T> key, T value){
    synchronized(this){
      if (myUserMap == null){
        if (value == null) return;
        myUserMap = new THashMap(1);
      }
      if (value != null){
        myUserMap.put(key, value);
      }
      else{
        myUserMap.remove(key);
        if (myUserMap.size() == 0){
          myUserMap = null;
        }
      }
    }
  }

  public long getPhysicalLength() throws IOException {
    if (myLength == -1) {
      myLength = LocalFileSystem.getInstance().physicalLength(myVirtualFile);
    }

    return myLength;
  }
}
