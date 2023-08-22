// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.SavingRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class VFileEvent {
  private final boolean myIsFromRefresh;
  private final Object myRequestor;
  private String myCachedPath;

  public VFileEvent(Object requestor, final boolean isFromRefresh) {
    myRequestor = requestor;
    myIsFromRefresh = isFromRefresh;
  }

  public boolean isFromRefresh() {
    return myIsFromRefresh;
  }

  /**
   * Returns {@code true} if the VFS change described by the event is the save of a document.
   */
  public boolean isFromSave() {
    return myRequestor instanceof SavingRequestor;
  }

  public Object getRequestor() {
    return myRequestor;
  }

  /**
   * Returns the file path (in system independent format) affected by this event.<br/><br/>
   *
   * Note that the path might be cached, thus can become out-of-date if requested later,
   * asynchronously from the event dispatching procedure
   * (e.g. {@code event.getPath()} can become not equal to {@code event.getFile().getPath()}).
   */
  public @NotNull String getPath() {
    String path = myCachedPath;
    if (path == null) {
      myCachedPath = path = computePath();
    }
    return path;
  }

  protected abstract @NotNull String computePath();

  /**
   * Returns the VirtualFile which this event belongs to.
   * In some cases it may be null - it is not guaranteed that there is such file.
   * <p/>
   * NB: Use this method with caution, because {@link VFileCreateEvent#getFile()} needs
   * {@link VirtualFile#findChild(String)} which may be a performance leak.
   */
  public abstract @Nullable VirtualFile getFile();

  public abstract @NotNull VirtualFileSystem getFileSystem();

  public abstract boolean isValid();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object o);
}
