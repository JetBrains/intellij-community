/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link RootDetector} which detects a root by presence of files of some specified type under it
 *
 * @author nik
 */
public class FileTypeBasedRootFilter extends RootFilter {
  private final FileType myFileType;

  public FileTypeBasedRootFilter(OrderRootType rootType, boolean jarDirectory, @NotNull FileType fileType,
                                 final String presentableRootTypeName) {
    super(rootType, jarDirectory, presentableRootTypeName);
    myFileType = fileType;
  }

  @Override
  public boolean isAccepted(@NotNull VirtualFile rootCandidate, @NotNull final ProgressIndicator progressIndicator) {
    if (isJarDirectory()) {
      if (!rootCandidate.isDirectory() || !rootCandidate.isInLocalFileSystem()) {
        return false;
      }
      for (VirtualFile child : rootCandidate.getChildren()) {
        if (!child.isDirectory() && child.getFileType().equals(FileTypes.ARCHIVE)) {
          final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(child);
          if (jarRoot != null && containsFileOfType(jarRoot, progressIndicator)) {
            return true;
          }
        }
      }
      return false;
    }
    else {
      return containsFileOfType(rootCandidate, progressIndicator);
    }
  }

  private boolean containsFileOfType(VirtualFile rootCandidate, final ProgressIndicator progressIndicator) {
    return !VfsUtil.processFilesRecursively(rootCandidate, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile virtualFile) {
        progressIndicator.checkCanceled();
        if (virtualFile.isDirectory()) {
          progressIndicator.setText2(virtualFile.getPath());
          return true;
        }
        return !isFileAccepted(virtualFile);
      }
    });
  }

  protected boolean isFileAccepted(VirtualFile virtualFile) {
    return virtualFile.getFileType().equals(myFileType);
  }
}
