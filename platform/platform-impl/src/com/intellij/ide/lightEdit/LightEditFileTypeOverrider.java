// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditFileTypeOverrider implements FileTypeOverrider {
  @Override
  public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
    return LightEditService.getInstance().getExplicitFileType(file);
  }
}
