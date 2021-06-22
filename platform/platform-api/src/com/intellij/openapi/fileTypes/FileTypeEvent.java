/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @ApiStatus.Internal
  public FileTypeEvent(@NotNull Object source,
                       @Nullable FileType addedFileType,
                       @Nullable FileType removedFileType) {
    super(source);
    myAddedFileType = addedFileType;
    myRemovedFileType = removedFileType;
  }

  /**
   * If this event was triggered by a file type being added, returns the file type that has been added. Not available in
   * beforeFileTypeChanged listeners.
   */
  @Nullable
  public FileType getAddedFileType() {
    return myAddedFileType;
  }

  /**
   * If this event was triggered by a file type being removed, returns the file type that has been removed. Not available in
   * beforeFileTypeChanged listeners.
   */
  @Nullable
  public FileType getRemovedFileType() {
    return myRemovedFileType;
  }

  @Override
  public String toString() {
    var result = new SmartList<String>();
    if (myAddedFileType != null) {
      result.add("added file type = " + myAddedFileType);
    }
    if (myRemovedFileType != null) {
      result.add("removed file type = " + myRemovedFileType);
    }

    return "FileTypeEvent[" + StringUtil.join(result, ", ") +  "]";
  }
}
