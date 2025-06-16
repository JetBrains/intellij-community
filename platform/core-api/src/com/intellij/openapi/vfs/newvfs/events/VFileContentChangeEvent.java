// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class VFileContentChangeEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final long myOldModificationStamp;
  private final long myNewModificationStamp;
  private final long myOldTimestamp;
  private final long myNewTimestamp;
  private final long myOldLength;
  private final long myNewLength;

  @ApiStatus.Internal
  public static final int UNDEFINED_TIMESTAMP_OR_LENGTH = -1;

  /** @deprecated use {@link VFileContentChangeEvent#VFileContentChangeEvent(Object, VirtualFile, long, long)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("unused")
  public VFileContentChangeEvent(Object requestor, @NotNull VirtualFile file, long oldModificationStamp, long newModificationStamp, boolean isFromRefresh) {
    this(requestor, file, oldModificationStamp, newModificationStamp);
  }

  /** if newModificationStamp == UNDEFINED_TIMESTAMP_OR_LENGTH (-1), the new modification stamp will be generated from {@link LocalTimeCounter}*/
  @ApiStatus.Internal
  public VFileContentChangeEvent(Object requestor, @NotNull VirtualFile file, long oldModificationStamp, long newModificationStamp) {
    this(requestor, file, oldModificationStamp, newModificationStamp, UNDEFINED_TIMESTAMP_OR_LENGTH, UNDEFINED_TIMESTAMP_OR_LENGTH,
         UNDEFINED_TIMESTAMP_OR_LENGTH, UNDEFINED_TIMESTAMP_OR_LENGTH);
  }

  @ApiStatus.Internal
  public VFileContentChangeEvent(
    Object requestor,
    @NotNull VirtualFile file,
    long oldModificationStamp,
    long newModificationStamp,
    long oldTimestamp,
    long newTimestamp,
    long oldLength,
    long newLength
  ) {
    super(requestor);
    myFile = file;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp == UNDEFINED_TIMESTAMP_OR_LENGTH ? LocalTimeCounter.currentTime() : newModificationStamp;
    myOldTimestamp = oldTimestamp;
    myNewTimestamp = newTimestamp;
    myOldLength = oldLength;
    myNewLength = newLength;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public long getModificationStamp() {
    return myNewModificationStamp;
  }

  public long getOldModificationStamp() {
    return myOldModificationStamp;
  }

  public long getOldTimestamp() {
    return myOldTimestamp;
  }

  public long getNewTimestamp() {
    return myNewTimestamp;
  }

  public long getOldLength() {
    return myOldLength;
  }

  public long getNewLength() {
    return myNewLength;
  }

  public boolean isLengthAndTimestampDiffProvided() {
    return myOldTimestamp != UNDEFINED_TIMESTAMP_OR_LENGTH || myNewTimestamp != UNDEFINED_TIMESTAMP_OR_LENGTH ||
           myOldLength != UNDEFINED_TIMESTAMP_OR_LENGTH || myNewLength != UNDEFINED_TIMESTAMP_OR_LENGTH;
  }

  @Override
  public String toString() {
    return "VfsEvent[update: " + myFile.getPresentableUrl() +
           ", oldTimestamp:" + myOldTimestamp + ", newTimestamp:" + myNewTimestamp +
           ", oldModificationStamp:" + myOldModificationStamp + ", newModificationStamp:" + myNewModificationStamp +
           ", oldLength:" + myOldLength + ", newLength:" + myNewLength +
           "]";
  }

  @Override
  protected @NotNull String computePath() {
    return myFile.getPath();
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && myFile.getModificationStamp() == myOldModificationStamp;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileContentChangeEvent event = (VFileContentChangeEvent)o;

    if (myNewModificationStamp != event.myNewModificationStamp) return false;
    if (myOldModificationStamp != event.myOldModificationStamp) return false;
    if (!myFile.equals(event.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + Long.hashCode(myOldModificationStamp);
    result = 31 * result + Long.hashCode(myNewModificationStamp);
    return result;
  }
}
