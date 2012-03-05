/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;

public class VirtualFileFilteringListener implements VirtualFileListener {
  private final VirtualFileListener myDelegate;
  private final VirtualFileSystem myFilter;

  public VirtualFileFilteringListener(@NotNull VirtualFileListener delegate, @NotNull VirtualFileSystem filter) {
    myDelegate = delegate;
    myFilter = filter;
  }

  private boolean isGood(VirtualFileEvent event) {
    return event.getFile().getFileSystem() == myFilter;
  }

  @Override
  public void beforeContentsChange(final VirtualFileEvent event) {
    if (isGood(event)) {
      myDelegate.beforeContentsChange(event);
    }
  }

  @Override
  public void beforeFileDeletion(final VirtualFileEvent event) {
    if (isGood(event)) {
      myDelegate.beforeFileDeletion(event);
    }
  }

  @Override
  public void beforeFileMovement(final VirtualFileMoveEvent event) {
    if (isGood(event)) {
      myDelegate.beforeFileMovement(event);
    }
  }

  @Override
  public void beforePropertyChange(final VirtualFilePropertyEvent event) {
    if (isGood(event)) {
      myDelegate.beforePropertyChange(event);
    }
  }

  @Override
  public void contentsChanged(final VirtualFileEvent event) {
    if (isGood(event)) {
      myDelegate.contentsChanged(event);
    }
  }

  @Override
  public void fileCopied(final VirtualFileCopyEvent event) {
    if (isGood(event)) {
      myDelegate.fileCopied(event);
    }
  }

  @Override
  public void fileCreated(final VirtualFileEvent event) {
    if (isGood(event)) {
      myDelegate.fileCreated(event);
    }
  }

  @Override
  public void fileDeleted(final VirtualFileEvent event) {
    if (isGood(event)) {
      myDelegate.fileDeleted(event);
    }
  }

  @Override
  public void fileMoved(final VirtualFileMoveEvent event) {
    if (isGood(event)) {
      myDelegate.fileMoved(event);
    }
  }

  @Override
  public void propertyChanged(final VirtualFilePropertyEvent event) {
    if (isGood(event)) {
      myDelegate.propertyChanged(event);
    }
  }
}