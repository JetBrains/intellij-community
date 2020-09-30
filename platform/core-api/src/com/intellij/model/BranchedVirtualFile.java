// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class BranchedVirtualFile extends LightVirtualFile {

  BranchedVirtualFile(@Nullable VirtualFile original, @NotNull String name) {
    super(name, null, "", original == null ? 0 : original.getModificationStamp());
  }

  @NotNull
  protected abstract ModelBranch getBranch();


}