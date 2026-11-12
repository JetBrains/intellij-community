// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public final class FileTypeEvent extends EventObject {
  private final FileType myAddedFileType;
  private final FileType myRemovedFileType;
  private final String myAddedFileTypeName;

  @ApiStatus.Internal
  public FileTypeEvent(@NotNull Object source,
                       @Nullable FileType addedFileType,
                       @Nullable FileType removedFileType,
                       @Nullable String addedFileTypeName) {
    super(source);
    myAddedFileType = addedFileType;
    myRemovedFileType = removedFileType;
    myAddedFileTypeName = addedFileTypeName;
  }

  /**
   * Returns the added file type, or {@code null} if it is still pending or no file type was added.
   * Events sent before a change return {@code null}.
   */
  public @Nullable FileType getAddedFileType() {
    return myAddedFileType;
  }

  /**
   * Returns the name of the added file type, including a pending type, or {@code null} if no file type was added.
   */
  @Nullable String getAddedFileTypeName() {
    return myAddedFileType != null ? myAddedFileType.getName() : myAddedFileTypeName;
  }

  /**
   * If this event was triggered by a file type being removed, returns the file type that has been removed. Not available in
   * beforeFileTypeChanged listeners.
   */
  public @Nullable FileType getRemovedFileType() {
    return myRemovedFileType;
  }

  @Override
  public String toString() {
    var result = new SmartList<String>();
    if (myAddedFileType != null) {
      result.add("added file type = " + myAddedFileType);
    }
    else if (myAddedFileTypeName != null) {
      result.add("added file type = " + myAddedFileTypeName);
    }
    if (myRemovedFileType != null) {
      result.add("removed file type = " + myRemovedFileType);
    }

    return "FileTypeEvent[" + StringUtil.join(result, ", ") +  "]";
  }
}
