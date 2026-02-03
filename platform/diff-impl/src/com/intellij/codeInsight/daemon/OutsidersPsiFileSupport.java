// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *
 * @deprecated use {@link SyntheticPsiFileSupport} instead
 */
@Deprecated(forRemoval = true)
public final class OutsidersPsiFileSupport {
  public static void markFile(@NotNull VirtualFile file, @Nullable String originalPath) {
    SyntheticPsiFileSupport.markFile(file, originalPath);
  }

  public static boolean isOutsiderFile(@Nullable VirtualFile file) {
    return SyntheticPsiFileSupport.isOutsiderFile(file);
  }

  public static @Nullable String getOriginalFilePath(@NotNull VirtualFile file) {
    return SyntheticPsiFileSupport.getOriginalFilePath(file);
  }
}
