// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class VFileContentChangeEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final long myOldModificationStamp;
  private final long myNewModificationStamp;
  private final long myOldTimestamp;
  private final long myNewTimestamp;
  private final long myOldLength;
  private final long myNewLength;

  private static final int UNDEFINED_TIMESTAMP_OR_LENGTH = -1;

  public VFileContentChangeEvent(Object requestor,
                                 @NotNull VirtualFile file,
                                 long oldModificationStamp,
                                 long newModificationStamp,
                                 boolean isFromRefresh) {
    this(requestor, file, oldModificationStamp, newModificationStamp, UNDEFINED_TIMESTAMP_OR_LENGTH, UNDEFINED_TIMESTAMP_OR_LENGTH,
         UNDEFINED_TIMESTAMP_OR_LENGTH, UNDEFINED_TIMESTAMP_OR_LENGTH, isFromRefresh);
  }

  public VFileContentChangeEvent(Object requestor,
                                 @NotNull VirtualFile file,
                                 long oldModificationStamp,
                                 long newModificationStamp,
                                 long oldTimestamp,
                                 long newTimestamp,
                                 long oldLength,
                                 long newLength,
                                 boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp == UNDEFINED_TIMESTAMP_OR_LENGTH ? LocalTimeCounter.currentTime() : newModificationStamp;
    myOldTimestamp = oldTimestamp;
    myNewTimestamp = newTimestamp;
    myOldLength = oldLength;
    myNewLength = newLength;
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
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

  @NonNls
  public String toString() {
    return "VfsEvent[update: " + myFile.getPresentableUrl() +
           ", oldTimestamp:" + myOldTimestamp + ", newTimestamp:" + myNewTimestamp +
           ", oldModificationStamp:" + myOldModificationStamp + ", newModificationStamp:" + myNewModificationStamp +
           ", oldLength:" + myOldLength + ", newLength:" + myNewLength +
           "]";
  }

  @NotNull
  @Override
  protected String computePath() {
    return myFile.getPath();
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && myFile.getModificationStamp() == myOldModificationStamp;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileContentChangeEvent event = (VFileContentChangeEvent)o;

    if (myNewModificationStamp != event.myNewModificationStamp) return false;
    if (myOldModificationStamp != event.myOldModificationStamp) return false;
    if (!myFile.equals(event.myFile)) return false;

    return true;
  }

  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + (int)(myOldModificationStamp ^ (myOldModificationStamp >>> 32));
    result = 31 * result + (int)(myNewModificationStamp ^ (myNewModificationStamp >>> 32));
    return result;
  }
}
