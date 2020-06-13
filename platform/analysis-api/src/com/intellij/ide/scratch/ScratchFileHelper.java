// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public final class ScratchFileHelper {
  public static boolean isScratchFile(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return false;
    }
    return "Scratch".equals(virtualFile.getFileType().getName());
  }
}
