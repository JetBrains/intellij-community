// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

/**
 * Provides data for a virtual file system change event.
 *
 * @see VirtualFileListener
 */
public class VirtualFileEvent extends EventObject {
  private final Object myRequestor;
  private final VirtualFile myFile;
  private final VirtualFile myParent;

  private final long myOldModificationStamp;
  private final long myNewModificationStamp;

  public VirtualFileEvent(@Nullable Object requestor,
                          @NotNull VirtualFile file,
                          @Nullable VirtualFile parent,
                          long oldModificationStamp,
                          long newModificationStamp) {
    super(file);
    myRequestor = requestor;
    myFile = file;
    myParent = parent;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp;
  }

  /**
   * Returns the file to which the change happened.
   */
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  /**
   * Returns the name of the changed file.
   */
  public @NotNull String getFileName() {
    return myFile.getName();
  }

  /**
   * Returns the parent of the virtual file, or {@code null} if the file is a root directory
   * or it was not possible to determine the parent (depends on the specific VFS implementation).
   */
  public @Nullable VirtualFile getParent() {
    return myParent;
  }

  /**
   * Returns the object that requested the operation changing the VFS, or {@code null} if the change was
   * caused by an external process and detected during VFS refresh.
   */
  public @Nullable Object getRequestor() {
    return myRequestor;
  }

  /**
   * Returns the modification stamp of the file before the event.
   *
   * @see VirtualFile#getModificationStamp()
   */
  public long getOldModificationStamp() {
    return myOldModificationStamp;
  }

  /**
   * Returns the modification stamp of the file after the event.
   *
   * @see VirtualFile#getModificationStamp()
   */
  public long getNewModificationStamp() {
    return myNewModificationStamp;
  }

  public boolean isFromRefresh() {
    return myRequestor == null;
  }

  /**
   * Returns {@code true} if the VFS change described by the event is the save of a document.
   */
  public boolean isFromSave() {
    return myRequestor instanceof SavingRequestor;
  }
}