
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.DeprecatedVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.DummyFileIdGenerator;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
abstract class VirtualFileImpl extends DeprecatedVirtualFile implements VirtualFileWithId {
  private final DummyFileSystem myFileSystem;
  private final VirtualFileDirectoryImpl myParent;
  private String myName;
  protected boolean myIsValid = true;
  private int myId = DummyFileIdGenerator.next();

  protected VirtualFileImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    myFileSystem = fileSystem;
    myParent = parent;
    myName = name;
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    if (myParent == null) {
      return myName;
    } else {
      return myParent.getPath() + "/" + myName;
    }
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(final String name) {
    myName = name;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isValid() {
    return myIsValid;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  public long getTimeStamp() {
    return -1;
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }
}
