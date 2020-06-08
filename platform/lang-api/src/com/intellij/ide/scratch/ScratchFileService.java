// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class ScratchFileService {
  public enum Option {existing_only, create_if_missing, create_new_always}

  public static ScratchFileService getInstance() {
    return ServiceHolder.instance;
  }

  private static final class ServiceHolder {
    static final ScratchFileService instance = ServiceManager.getService(ScratchFileService.class);
  }

  @NotNull
  public abstract String getRootPath(@NotNull RootType rootType);

  @Nullable
  public abstract RootType getRootType(@Nullable VirtualFile file);

  public abstract VirtualFile findFile(@NotNull RootType rootType, @NotNull String pathName, @NotNull Option option) throws IOException;

  @NotNull
  public abstract PerFileMappings<Language> getScratchesMapping();

  @Nullable
  public static RootType findRootType(@Nullable VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) return null;
    VirtualFile parent = file.isDirectory() ? file : file.getParent();
    return getInstance().getRootType(parent);
  }

  /** @deprecated use {@link ScratchFileService#findRootType(VirtualFile)} or {@link ScratchUtil#isScratch(VirtualFile)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean isInScratchRoot(@Nullable VirtualFile file) {
    return findRootType(file) != null;
  }
}
