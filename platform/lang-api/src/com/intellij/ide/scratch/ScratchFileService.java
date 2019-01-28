// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class ScratchFileService {
  public enum Option {existing_only, create_if_missing, create_new_always}

  public static ScratchFileService getInstance() {
    return ServiceHolder.instance;
  }

  private static class ServiceHolder {
    static final ScratchFileService instance = ServiceManager.getService(ScratchFileService.class);
  }

  @NotNull
  public abstract String getRootPath(@NotNull RootType rootType);

  @Nullable
  public abstract RootType getRootType(@Nullable VirtualFile file);

  public abstract VirtualFile findFile(@NotNull RootType rootType, @NotNull String pathName, @NotNull Option option) throws IOException;

  @NotNull
  public abstract PerFileMappings<Language> getScratchesMapping();

  public static boolean isInScratchRoot(@Nullable VirtualFile file) {
    VirtualFile parent = file == null ? null : file.getParent();
    if (parent == null || !file.isInLocalFileSystem()) {
      return false;
    }
    return getInstance().getRootType(file) != null;
  }
}
