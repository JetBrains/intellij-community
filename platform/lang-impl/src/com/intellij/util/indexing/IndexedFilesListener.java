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
package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class IndexedFilesListener implements BulkFileListener {
  private final ManagingFS myManagingFS = ManagingFS.getInstance();
  @Nullable private final String myConfigPath;
  @Nullable private final String myLogPath;

  public IndexedFilesListener() {
    myConfigPath = calcConfigPath(PathManager.getConfigPath());
    myLogPath = calcConfigPath(PathManager.getLogPath());
  }

  protected void buildIndicesForFileRecursively(@NotNull final VirtualFile file, final boolean contentChange) {
    if (file.isDirectory()) {
      final ContentIterator iterator = fileOrDir -> {
        buildIndicesForFile(fileOrDir, contentChange);
        return true;
      };

      iterateIndexableFiles(file, iterator);
    }
    else {
      buildIndicesForFile(file, contentChange);
    }
  }

  protected boolean invalidateIndicesForFile(@NotNull VirtualFile file, boolean contentChange) {
    if (isUnderConfigOrSystem(file)) {
      return false;
    }
    if (file.isDirectory()) {
      doInvalidateIndicesForFile(file, contentChange);
      if (!FileBasedIndexImpl.isMock(file) && !myManagingFS.wereChildrenAccessed(file)) {
        return false;
      }
    }
    else {
      doInvalidateIndicesForFile(file, contentChange);
    }
    return true;
  }

  protected abstract void iterateIndexableFiles(@NotNull VirtualFile file, @NotNull ContentIterator iterator);
  protected abstract void buildIndicesForFile(@NotNull VirtualFile file, boolean contentChange);
  protected abstract void doInvalidateIndicesForFile(@NotNull VirtualFile file, boolean contentChange);

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
      if (event instanceof VFileContentChangeEvent) {
        invalidateIndicesRecursively(((VFileContentChangeEvent)event).getFile(), true);
      }
      else if (event instanceof VFileDeleteEvent) {
        invalidateIndicesRecursively(((VFileDeleteEvent)event).getFile(), false);
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          // name change may lead to filetype change so the file might become not indexable
          // in general case have to 'unindex' the file and index it again if needed after the name has been changed
          invalidateIndicesRecursively(pce.getFile(), false);
        } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          invalidateIndicesRecursively(pce.getFile(), true);
        }
      }
    }
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (event instanceof VFileContentChangeEvent) {
        buildIndicesForFileRecursively(((VFileContentChangeEvent)event).getFile(), true);
      }
      else if (event instanceof VFileCopyEvent) {
        final VFileCopyEvent ce = (VFileCopyEvent)event;
        final VirtualFile copy = ce.getNewParent().findChild(ce.getNewChildName());
        if (copy != null) {
          buildIndicesForFileRecursively(copy, false);
        }
      }
      else if (event instanceof VFileCreateEvent) {
        final VirtualFile newChild = event.getFile();
        if (newChild != null) {
          buildIndicesForFileRecursively(newChild, false);
        }
      }
      else if (event instanceof VFileMoveEvent) {
        buildIndicesForFileRecursively(((VFileMoveEvent)event).getFile(), false);
      }
      else if (event instanceof VFilePropertyChangeEvent) {
        final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
        String propertyName = pce.getPropertyName();
        if (propertyName.equals(VirtualFile.PROP_NAME)) {
          // indexes may depend on file name
          buildIndicesForFileRecursively(pce.getFile(), false);
        } else if (propertyName.equals(VirtualFile.PROP_ENCODING)) {
          buildIndicesForFileRecursively(pce.getFile(), true);
        }
      }
    }
  }

  @Nullable
  private static String calcConfigPath(@NotNull String path) {
    try {
      final String _path = FileUtil.toSystemIndependentName(new File(path).getCanonicalPath());
      return _path.endsWith("/") ? _path : _path + "/";
    }
    catch (IOException e) {
      FileBasedIndexImpl.LOG.info(e);
      return null;
    }
  }

  private boolean isUnderConfigOrSystem(@NotNull VirtualFile file) {
    final String filePath = file.getPath();
    return myConfigPath != null && FileUtil.startsWith(filePath, myConfigPath) ||
           myLogPath != null && FileUtil.startsWith(filePath, myLogPath);
  }
}