// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.transformer

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface TextPresentationTransformer {
  /**
   * Transforms the given text from its persistent representation to its in-memory representation
   * based on the specified virtual file's context.
   *
   * Used in Jupyter Notebooks to switch from JSON representation to source code text with #%% separators between cells.
   *
   * @param text The text in its persistent representation to be transformed
   * @param virtualFile The virtual file providing context for the transformation
   * @return The text in its in-memory representation
   */
  fun fromPersistent(text: CharSequence, virtualFile: VirtualFile): CharSequence

  /**
   * Transforms the given text from its in-memory representation to its persistent representation
   * based on the context provided by the specified virtual file.
   *
   * Used in Jupyter Notebooks to switch from source code text with #%% separators between cells to JSON representation.
   *
   * @param text The text in its in-memory representation to be transformed
   * @param virtualFile The virtual file providing context for the transformation
   * @return The text in its persistent representation
   */
  fun toPersistent(text: CharSequence, virtualFile: VirtualFile): CharSequence
}