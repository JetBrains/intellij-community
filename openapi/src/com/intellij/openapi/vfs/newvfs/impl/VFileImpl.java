/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.VirtualFileManagement;
import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class VFileImpl extends NewVirtualFile {
  private volatile NewVirtualFileSystem myFS;
  private volatile String myName;
  private volatile VFileImpl myParent;
  private volatile WeakValueHashMap<String, VirtualFile> myChildrenCache;
  private volatile int myId;

  public VFileImpl(final String name, final VirtualFile parent, final NewVirtualFileSystem fs, int id) {
    myFS = fs;
    myName = name;
    myParent = (VFileImpl)parent;
    myId = id;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  @NotNull
  public NewVirtualFileSystem getFileSystem() {
    return myFS;
  }

  private void appendPathOnFileSystem(StringBuilder builder, boolean includeFSBaseUrl) {
    if (myParent != null) {
      myParent.appendPathOnFileSystem(builder, includeFSBaseUrl);
      builder.append('/');
    }
    else if (includeFSBaseUrl) {
      final String baseUrl = myFS.getBaseUrl();
      builder.append(baseUrl);
      if (!baseUrl.endsWith("/")) {
        builder.append('/');
      }
    }

    builder.append(myName);
  }

  @NotNull
  public String getUrl() {
    StringBuilder builder = new StringBuilder();
    appendPathOnFileSystem(builder, true);
    return builder.toString();
  }

  @NotNull
  public String getPath() {
    StringBuilder builder = new StringBuilder();
    appendPathOnFileSystem(builder, false);
    return builder.toString();
  }

  @NotNull
  public VirtualFile createChildData(final Object requestor, final String name) throws IOException {
    return getFileSystem().createChildFile(requestor, this, name);
  }

  public VirtualFile createChildFileInstance(final String name, int id) {
    if (myChildrenCache == null) {
      myChildrenCache = getFileSystem().isCaseSensitive()
                        ? new WeakValueHashMap<String, VirtualFile>()
                        : new WeakValueHashMap<String, VirtualFile>(CaseInsensitiveStringHashingStrategy.INSTANCE);
    }

    VirtualFile child = myChildrenCache.get(name);
    if (child == null) {
      final NewVirtualFileSystem fs = VirtualFileManagement.getInstance().findFSForChild(this, name);
      child = new VFileImpl(name, this, fs, id != -1 ? id : fs.getId(this, name));
      myChildrenCache.put(name, child);
    }

    return child;
  }

  public boolean isWritable() {
    return getFileSystem().isWritable(this);
  }

  public long getTimeStamp() {
    return getFileSystem().getTimeStamp(this);
  }

  public void setTimeStamp(final long time) throws IOException {
    getFileSystem().setTimeStamp(this, time);
  }

  public long getLength() {
    return getFileSystem().getLength(this);
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, final String name) throws IOException {
    return getFileSystem().createChildDirectory(requestor, this, name);
  }

  public boolean exists() {
    return getFileSystem().exists(this);
  }

  @Nullable
  public VirtualFile findChild(final String name) {
    VirtualFile child = createChildFileInstance(name, -1);
    if (child.exists()) return child;
    return null;
  }

  @NotNull
  public VirtualFile[] getChildren() {
    return getFileSystem().listFiles(this);
  }


  @NotNull
  public InputStream getInputStream() throws IOException {
    return getFileSystem().getInputStream(this);
  }

  @NotNull
  public OutputStream getOutputStream(final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    return getFileSystem().getOutputStream(this, requestor, modStamp, timeStamp);
  }

  public boolean isDirectory() {
    return getFileSystem().isDirectory(this);
  }

  public boolean isValid() {
    return exists();
  }

  public void setVFS(NewVirtualFileSystem newVfs) {
    if (newVfs != myFS) {
      myFS = newVfs;
      myId = newVfs.getId(getParent(), getName());
      if (myChildrenCache != null) {
        final VirtualFileManagement management = VirtualFileManagement.getInstance();
        for (VirtualFile child : myChildrenCache.values()) {
          if (management.findFSForChild(this, child.getName()) == newVfs) {
            ((VFileImpl)child).setVFS(newVfs);
          }
        }
      }
    }
  }

  public String toString() {
    return getUrl();
  }

  public void setName(final String newName) {
    myName = newName;
  }

  public void setParent(final VirtualFile newParent) {
    VFileImpl oldParent = myParent;
    VFileImpl realParent = (VFileImpl)newParent;
    /*
    if (oldParent.myChildren != null) {
      final int idx = ArrayUtil.indexOf(myChildren, this);
      if (idx >= 0) {
        if (oldParent == newParent) return;

        oldParent.myChildren = ArrayUtil.remove(oldParent.myChildren, idx);
      }
    }

    if (realParent != null && realParent.myChildren != null) {
      realParent.myChildren = ArrayUtil.append(realParent.myChildren, this);
    }

    */
    myParent = realParent;
  }
}