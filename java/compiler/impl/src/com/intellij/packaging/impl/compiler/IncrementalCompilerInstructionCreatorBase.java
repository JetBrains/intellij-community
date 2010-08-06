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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.packaging.elements.PackagingFileFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public abstract class IncrementalCompilerInstructionCreatorBase implements IncrementalCompilerInstructionCreator {
  protected final ArtifactsProcessingItemsBuilderContext myContext;

  public IncrementalCompilerInstructionCreatorBase(ArtifactsProcessingItemsBuilderContext context) {
    myContext = context;
  }

  public void addDirectoryCopyInstructions(@NotNull VirtualFile directory) {
    addDirectoryCopyInstructions(directory, null);
  }

  public void addDirectoryCopyInstructions(@NotNull VirtualFile directory, @Nullable PackagingFileFilter filter) {
    ProjectFileIndex index = ProjectRootManager.getInstance(myContext.getCompileContext().getProject()).getFileIndex();
    final boolean copyExcluded = index.isIgnored(directory);
    collectInstructionsRecursively(directory, filter, index, FileTypeManager.getInstance(), copyExcluded);
  }

  private void collectInstructionsRecursively(VirtualFile directory,
                                              PackagingFileFilter filter,
                                              ProjectFileIndex index,
                                              final FileTypeManager fileTypeManager,
                                              boolean copyExcluded) {
    final VirtualFile[] children = directory.getChildren();
    if (children != null) {
      for (VirtualFile child : children) {
        if (copyExcluded) {
          if (fileTypeManager.isFileIgnored(child.getName())) continue;
        }
        else {
          if (index.isIgnored(child)) continue;
        }

        if ((filter == null || filter.accept(child, myContext.getCompileContext()))) {
          if (!child.isDirectory()) {
            addFileCopyInstruction(child, child.getName());
          }
          else {
            subFolder(child.getName()).collectInstructionsRecursively(child, filter, index, fileTypeManager, copyExcluded);
          }
        }
      }
    }
  }

  @Override
  public abstract IncrementalCompilerInstructionCreatorBase subFolder(@NotNull String directoryName);

  public IncrementalCompilerInstructionCreator subFolderByRelativePath(@NotNull String relativeDirectoryPath) {
    final List<String> folders = StringUtil.split(relativeDirectoryPath, "/");
    IncrementalCompilerInstructionCreator current = this;
    for (String folder : folders) {
      current = current.subFolder(folder);
    }
    return current;
  }
}
