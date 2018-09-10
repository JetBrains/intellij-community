/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/**
 * @author Irina.Chernushina on 1/20/2017.
 */
public abstract class VirtualFileContentsChangedAdapter implements VirtualFileListener {
  @Override
  public void contentsChanged(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
    onBeforeFileChange(event.getFile());
  }

  @Override
  public void beforeFileMovement(@NotNull VirtualFileMoveEvent event) {
    onBeforeFileChange(event.getFile());
  }

  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void fileCopied(@NotNull VirtualFileCopyEvent event) {
    onFileChange(event.getFile());
  }

  protected abstract void onFileChange(@NotNull final VirtualFile fileOrDirectory);
  protected abstract void onBeforeFileChange(@NotNull final VirtualFile fileOrDirectory);

  @Override
  public void fileDeleted(@NotNull VirtualFileEvent event) {
    onFileChange(event.getFile());
  }

  @Override
  public void beforeContentsChange(@NotNull VirtualFileEvent event) {
    onBeforeFileChange(event.getFile());
  }
}
