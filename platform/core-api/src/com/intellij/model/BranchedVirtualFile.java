// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

class BranchedVirtualFile extends LightVirtualFile {
  @NotNull final VirtualFile original;
  @NotNull final ModelBranch branch;

  BranchedVirtualFile(@NotNull VirtualFile original, @NotNull CharSequence contents, @NotNull ModelBranch branch) {
    super(original.getName(), original.getFileType(), contents, original.getCharset(), original.getModificationStamp());
    this.original = original;
    this.branch = branch;
  }
}