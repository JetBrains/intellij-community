/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * You can implement the extension point to customize resolution of references in a context of a specific file.
 * There are two approaches here:
 * <ol>
 * <li>You can provide the resolution logic right inside of your file (e.g. HtmlFileImpl)
 * <li>You can provide an additional context (SyntheticFileSystemItem), which implements the interface
 * </ol>
 *
 * @see FileReferenceHelper
 */
public interface FileReferenceResolver {

  /**
   * @return resolution result for the name in the context of the reference.
   */
  @Nullable
  PsiFileSystemItem resolveFileReference(@NotNull FileReference reference, @NotNull String name);

  /**
   * @return additional variants for code completion
   */
  Collection<Object> getVariants(@NotNull FileReference reference);

  /**
   * Provides a way to customize file reference "bind" operation.
   * The path will be updated from the start of the reference set to the end of the reference.
   * The default implementation returns the first "relative" path, but in many cases it can be incorrectly.
   */
  @ApiStatus.Experimental
  @Nullable
  default String getRelativePath(@NotNull FileReference reference,
                                 @NotNull VirtualFile contextFile,
                                 @NotNull final PsiElement newElement) {
    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)newElement;
    final VirtualFile dstVFile = fileSystemItem.getVirtualFile();

    if (VfsUtilCore.isAncestor(contextFile, dstVFile, true)) {
      return VfsUtilCore.getRelativePath(dstVFile, contextFile, '/');
    }
    return null;
  }
}