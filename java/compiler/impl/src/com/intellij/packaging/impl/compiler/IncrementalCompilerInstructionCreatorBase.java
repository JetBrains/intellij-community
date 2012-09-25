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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
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
    final ProjectFileIndex index = ProjectRootManager.getInstance(myContext.getCompileContext().getProject()).getFileIndex();
    final boolean copyExcluded = index.isIgnored(directory);
    collectInstructionsRecursively(directory, this, filter, index, copyExcluded);
  }

  private static void collectInstructionsRecursively(VirtualFile directory,
                                                     final IncrementalCompilerInstructionCreatorBase creator,
                                                     @Nullable final PackagingFileFilter filter,
                                                     final ProjectFileIndex index,
                                                     final boolean copyExcluded) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VfsUtilCore.visitChildrenRecursively(directory, new VirtualFileVisitor<IncrementalCompilerInstructionCreatorBase>(VirtualFileVisitor.SKIP_ROOT) {
      { setValueForChildren(creator); }

      @Override
      public boolean visitFile(@NotNull VirtualFile child) {
        if (copyExcluded) {
          if (fileTypeManager.isFileIgnored(child)) return false;
        }
        else {
          if (index.isIgnored(child)) return false;
        }

        final IncrementalCompilerInstructionCreatorBase creator = getCurrentValue();
        if (filter != null && !filter.accept(child, creator.myContext.getCompileContext())) {
          return false;
        }

        if (!child.isDirectory()) {
          creator.addFileCopyInstruction(child, child.getName());
        }
        else {
          setValueForChildren(creator.subFolder(child.getName()));
        }

        return true;
      }
    });
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
