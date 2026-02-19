// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/**
 * @see VfsUtilCore#createCompactVirtualFileSet()
 */
public interface VirtualFileSet extends Set<VirtualFile> {
  @NotNull
  @Unmodifiable
  Set<VirtualFile> freezed();

  void freeze();

  boolean process(@NotNull Processor<? super VirtualFile> processor);
}
