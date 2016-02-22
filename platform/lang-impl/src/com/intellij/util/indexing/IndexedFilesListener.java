/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.indexing;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class IndexedFilesListener extends VirtualFileAdapter implements BulkFileListener {
  @Override
  public void fileMoved(@NotNull VirtualFileMoveEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void fileCreated(@NotNull final VirtualFileEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void fileCopied(@NotNull final VirtualFileCopyEvent event) {
    buildIndicesForFileRecursively(event.getFile(), false);
  }

  @Override
  public void beforeFileDeletion(@NotNull final VirtualFileEvent event) {
    invalidateIndicesRecursively(event.getFile(), false);
  }

  @Override
  public void beforeContentsChange(@NotNull final VirtualFileEvent event) {
    invalidateIndicesRecursively(event.getFile(), true);
  }

  @Override
  public void contentsChanged(@NotNull final VirtualFileEvent event) {
    buildIndicesForFileRecursively(event.getFile(), true);
  }

  @Override
  public void beforePropertyChange(@NotNull final VirtualFilePropertyEvent event) {
    String propertyName = event.getPropertyName();

    if (propertyName.equals(VirtualFile.PROP_NAME)) {
      // indexes may depend on file name
      // name change may lead to filetype change so the file might become not indexable
      // in general case have to 'unindex' the file and index it again if needed after the name has been changed
      invalidateIndicesRecursively(event.getFile(), false);
    } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
      invalidateIndicesRecursively(event.getFile(), true);
    }
  }

  @Override
  public void propertyChanged(@NotNull final VirtualFilePropertyEvent event) {
    String propertyName = event.getPropertyName();
    if (propertyName.equals(VirtualFile.PROP_NAME)) {
      // indexes may depend on file name
      buildIndicesForFileRecursively(event.getFile(), false);
    } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
      buildIndicesForFileRecursively(event.getFile(), true);
    }
  }

  protected void buildIndicesForFileRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    if (file.isDirectory()) {
      final ContentIterator iterator = new ContentIterator() {
        @Override
        public boolean processFile(@NotNull final VirtualFile fileOrDir) {
          buildIndicesForFile(fileOrDir, contentChange);
          return true;
        }
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      buildIndicesForFile(file, contentChange);
    }
  }

  protected abstract void iterateIndexableFiles(VirtualFile file, ContentIterator iterator);
  protected abstract void buildIndicesForFile(VirtualFile file, boolean contentChange);
  protected abstract boolean invalidateIndicesForFile(VirtualFile file, boolean contentChange);

  protected void invalidateIndicesRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return invalidateIndicesForFile(file, contentChange);
      }

      @Override
      public Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).iterInDbChildren() : null;
      }
    });
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      BulkVirtualFileListenerAdapter.fireBefore(this, event);
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      BulkVirtualFileListenerAdapter.fireAfter(this, event);
    }
  }
}