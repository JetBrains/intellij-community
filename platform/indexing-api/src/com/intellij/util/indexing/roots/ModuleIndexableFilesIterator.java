// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface ModuleIndexableFilesIterator extends IndexableFilesIterator {
  @NotNull Module getModule();

  @NotNull VirtualFile getRoot();
}
