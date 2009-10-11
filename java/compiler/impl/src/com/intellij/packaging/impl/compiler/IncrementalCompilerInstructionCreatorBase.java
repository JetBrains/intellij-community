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

import com.intellij.openapi.compiler.make.PackagingFileFilter;
import com.intellij.packaging.elements.IncrementalCompilerInstructionCreator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    final VirtualFile[] children = directory.getChildren();
    if (children != null) {
      for (VirtualFile child : children) {
        if (filter == null || filter.accept(child, myContext.getCompileContext())) {
          if (!child.isDirectory()) {
            addFileCopyInstruction(child, child.getName());
          }
          else {
            subFolder(child.getName()).addDirectoryCopyInstructions(child, filter);
          }
        }
      }
    }
  }
}
