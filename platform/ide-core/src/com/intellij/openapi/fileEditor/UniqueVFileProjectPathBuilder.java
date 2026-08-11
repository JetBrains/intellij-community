// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface UniqueVFileProjectPathBuilder {
  @NotNull
  @NlsSafe
  String getUniqueVirtualFilePath(@NotNull VirtualFile vFile, @NotNull GlobalSearchScope scope);

  @NotNull
  @NlsSafe
  String getUniqueVirtualFilePath(@NotNull VirtualFile vFile);

  @NotNull
  @NlsSafe
  String getUniqueVirtualFilePathWithinOpenedFileEditors(@NotNull VirtualFile vFile);
}
