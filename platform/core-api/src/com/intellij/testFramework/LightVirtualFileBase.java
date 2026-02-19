// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.openapi.vfs.VirtualFileWithAssignedFileType;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * In-memory implementation of {@link VirtualFile}.
 *
 * @see LightVirtualFile
 */
public abstract class LightVirtualFileBase extends VirtualFile implements VirtualFileWithAssignedFileType {
  private @Nullable FileType myFileType;
  private @NlsSafe @NotNull String myName;
  private long myModStamp;
  private boolean myIsWritable = true;
  private boolean myValid = true;
  private VirtualFile myOriginalFile;

  public LightVirtualFileBase(@NlsSafe @NotNull String name, @Nullable FileType fileType, long modificationStamp) {
    this(name, fileType, modificationStamp, DEFAULT_CREATION_TRACE);
  }

  /**
   * Use this constructor if you want to register a custom creation trace or avoid registering it at all.
   * Please don't pass {@code null} as the creation trace without a good reason.
   *
   * @param name the name of the file.
   * @param fileType the file type of the file.
   * @param modificationStamp the modification stamp of the file. The default option is {@link com.intellij.util.LocalTimeCounter#currentTime}
   * @param creationTrace the creation trace to register, {@code null}, or {@link #DEFAULT_CREATION_TRACE} to infer it from the current stack trace.
   */
  public LightVirtualFileBase(@NlsSafe @NotNull String name,
                              @Nullable FileType fileType,
                              long modificationStamp,
                              @Nullable Object creationTrace) {
    myName = name;
    myFileType = fileType;
    myModStamp = modificationStamp;
    registerCreationTrace(creationTrace);
  }

  public void setFileType(FileType fileType) {
    myFileType = fileType;
  }

   /**
   * @see VirtualFileUtil#originalFile(VirtualFile)
   * @see VirtualFileUtil#originalFileOrSelf(VirtualFile)
   */
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
    public @NotNull String getProtocol() {
      return PROTOCOL;
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull String path) {
      return null;
    }

    @Override
    public void refresh(boolean asynchronous) { }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
      return null;
    }
  }

  private static final MyVirtualFileSystem ourFileSystem = new MyVirtualFileSystem();

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return ourFileSystem;
  }

  @Override
  public @Nullable FileType getAssignedFileType() {
    return myFileType;
  }

  @Override
  public @NotNull String getPath() {
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
      throw new IncorrectOperationException("File is not writable: " + this);
    }
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    assertWritable();
    return super.createChildDirectory(requestor, name);
  }

  @Override
  public @NotNull VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
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

  private void registerCreationTrace(@Nullable Object creationTrace) {
    if (creationTrace == DEFAULT_CREATION_TRACE) {
      creationTrace = getDefaultCreationTrace();
    }

    if (creationTrace != null) {
      PsiInvalidElementAccessException.setCreationTrace(this, creationTrace);
    }
  }

  private static @Nullable Object getDefaultCreationTrace() {
    Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) {
      return null;
    }

    return new Throwable();
  }

  /** marker object saying that the creation trace should be inferred with the default algorithm */
  static final @NotNull Object DEFAULT_CREATION_TRACE = ObjectUtils.sentinel("default creation trace");
}