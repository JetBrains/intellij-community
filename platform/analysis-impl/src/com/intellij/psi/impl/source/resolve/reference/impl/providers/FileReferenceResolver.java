// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  default @Nullable String getRelativePath(@NotNull FileReference reference,
                                 @NotNull VirtualFile contextFile,
                                 final @NotNull PsiElement newElement) {
    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)newElement;
    final VirtualFile dstVFile = fileSystemItem.getVirtualFile();

    if (VfsUtilCore.isAncestor(contextFile, dstVFile, true)) {
      return VfsUtilCore.getRelativePath(dstVFile, contextFile, '/');
    }
    return null;
  }
}