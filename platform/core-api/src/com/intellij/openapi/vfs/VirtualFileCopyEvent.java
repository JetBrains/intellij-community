/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides data for event which is fired when a virtual file is copied.
 *
 * @see com.intellij.openapi.vfs.VirtualFileListener#fileCopied(com.intellij.openapi.vfs.VirtualFileCopyEvent)
 */
public class VirtualFileCopyEvent extends VirtualFileEvent {
  private final VirtualFile myOriginalFile;

  public VirtualFileCopyEvent(@Nullable Object requestor, @NotNull VirtualFile original, @NotNull VirtualFile created) {
    super(requestor, created, created.getName(), created.getParent());
    myOriginalFile = original;
  }

  /**
   * Returns original file.
   *
   * @return original file.
   */
  @NotNull
  public VirtualFile getOriginalFile() {
    return myOriginalFile;
  }
}
