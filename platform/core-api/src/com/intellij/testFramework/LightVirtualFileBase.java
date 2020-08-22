// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * In-memory implementation of {@link VirtualFile}.
 */
public abstract class LightVirtualFileBase extends VirtualFile {
  private FileType myFileType;
  private @NlsSafe String myName;
  private long myModStamp;
  private boolean myIsWritable = true;
  private boolean myValid = true;
  private VirtualFile myOriginalFile;

  public LightVirtualFileBase(final @NlsSafe String name, final FileType fileType, final long modificationStamp) {
    myName = name;
    myFileType = fileType;
    myModStamp = modificationStamp;
  }

  public void setFileType(FileType fileType) {
    myFileType = fileType;
  }

  public VirtualFile getOriginalFile() {
    return myOriginalFile;
  }

  public void setOriginalFile(VirtualFile originalFile) {
    myOriginalFile = originalFile;
  }

  private static final class MyVirtualFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {
    private static final @NonNls String PROTOCOL = "mock";

    private MyVirtualFileSystem() {
      startEventPropagation();
    }

    @Override
    @NotNull
    public String getProtocol() {
      return PROTOCOL;
    }

    @Override
    @Nullable
    public VirtualFile findFileByPath(@NotNull String path) {
      return null;
    }

    @Override
    public void refresh(boolean asynchronous) { }

    @Override
    @Nullable
    public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
      return null;
    }
  }

  private static final MyVirtualFileSystem ourFileSystem = new MyVirtualFileSystem();

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @Nullable
  public FileType getAssignedFileType() {
    return myFileType;
  }

  @NotNull
  @Override
  public String getPath() {
    VirtualFile parent = getParent();
    return (parent == null ? "" : parent.getPath()) + "/" + getName();
  }

  @Override
  public @NlsSafe @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean isWritable() {
    return myIsWritable;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myValid;
  }

  public void setValid(boolean valid) {
    myValid = valid;
  }

  @Override
  public VirtualFile getParent() {
    return null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public long getModificationStamp() {
    return myModStamp;
  }

  protected void setModificationStamp(long stamp) {
    myModStamp = stamp;
  }

  @Override
  public long getTimeStamp() {
    return 0; // todo[max] : Add UnsupportedOperationException at better times.
  }

  @Override
  public long getLength() {
    try {
      return contentsToByteArray().length;
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      assert false;
      return 0;
    }
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) { }

  @Override
  public void setWritable(boolean writable) {
    myIsWritable = writable;
  }

  @Override
  public void rename(Object requestor, @NotNull String newName) throws IOException {
    assertWritable();
    myName = newName;
  }

  void assertWritable() {
    if (!isWritable()) {
      throw new IncorrectOperationException("File is not writable: "+this);
    }
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    assertWritable();
    return super.createChildDirectory(requestor, name);
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
    assertWritable();
    return super.createChildData(requestor, name);
  }

  @Override
  public void delete(Object requestor) throws IOException {
    assertWritable();
    super.delete(requestor);
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    assertWritable();
    super.move(requestor, newParent);
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp) throws IOException {
    assertWritable();
    super.setBinaryContent(content, newModificationStamp, newTimeStamp);
  }

  @Override
  public void setBinaryContent(byte @NotNull [] content, long newModificationStamp, long newTimeStamp, Object requestor) throws IOException {
    assertWritable();
    super.setBinaryContent(content, newModificationStamp, newTimeStamp, requestor);
  }
}