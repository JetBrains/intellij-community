// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In the situation when the identifiers are provided by the binary file (implemented {@link com.intellij.psi.impl.cache.impl.id.IdIndexer}
 * for binary file) which corresponds to some source file, this extension is used to find the original source file during the Find Usages.
 */
public interface BinaryFileSourceProvider {
  ExtensionPointName<BinaryFileSourceProvider> EP = new ExtensionPointName<>("com.intellij.binaryFileSourceProvider");

  /**
   * Finds the original source file corresponding to the given binary file.
   *
   * @param file the binary file for which to find the source file
   * @return the original source file, or {@code null} if not found
   */
  @Nullable
  @RequiresReadLock
  PsiFile findSourceFile(@NotNull PsiBinaryFile file);
}
