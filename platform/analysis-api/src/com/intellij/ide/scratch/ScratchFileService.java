// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.PerFileMappings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

public abstract class ScratchFileService {
  public enum Option {existing_only, create_if_missing, create_new_always}

  private static ScratchFileService ourInstance = CachedSingletonsRegistry.markCachedField(ScratchFileService.class);

  public static ScratchFileService getInstance() {
    ScratchFileService result = ourInstance;
    if (result == null) {
      result = ApplicationManager.getApplication().getService(ScratchFileService.class);
      ourInstance = result;
    }
    return result;
  }

  public abstract @NotNull String getRootPath(@NotNull RootType rootType);

  public abstract @Nullable RootType getRootType(@Nullable VirtualFile file);

  public abstract VirtualFile findFile(@NotNull RootType rootType, @NotNull String pathName, @NotNull Option option) throws IOException;

  public abstract @NotNull PerFileMappings<Language> getScratchesMapping();

  public static @Nullable RootType findRootType(@Nullable VirtualFile file) {
    if (file == null || !file.isInLocalFileSystem()) return null;
    VirtualFile parent = file.isDirectory() ? file : file.getParent();
    return getInstance().getRootType(parent);
  }

  public static @NotNull Set<VirtualFile> getAllRootPaths() {
    ScratchFileService instance = getInstance();
    LocalFileSystem fileSystem = LocalFileSystem.getInstance();
    Set<VirtualFile> result = CollectionFactory.createSmallMemoryFootprintSet();
    for (RootType rootType : RootType.getAllRootTypes()) {
      if (rootType.isHidden()) continue;
      ContainerUtil.addIfNotNull(result, fileSystem.findFileByPath(instance.getRootPath(rootType)));
    }
    return result;
  }
}
