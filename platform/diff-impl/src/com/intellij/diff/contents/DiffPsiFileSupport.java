// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.contents;

import com.intellij.codeInsight.daemon.OutsidersPsiFileSupport;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

@Deprecated
public final class DiffPsiFileSupport {
  public static boolean isDiffFile(@Nullable VirtualFile file) {
    return OutsidersPsiFileSupport.isOutsiderFile(file);
  }
}
