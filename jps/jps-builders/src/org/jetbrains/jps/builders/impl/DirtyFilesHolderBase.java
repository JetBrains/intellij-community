// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApiStatus.Internal
public abstract class DirtyFilesHolderBase<R extends BuildRootDescriptor, T extends BuildTarget<R>> implements DirtyFilesHolder<R, T> {
  protected final CompileContext myContext;

  public DirtyFilesHolderBase(CompileContext context) {
    myContext = context;
  }

  @Override
  public boolean hasDirtyFiles() throws IOException {
    final Ref<Boolean> hasDirtyFiles = Ref.create(false);
    processDirtyFiles(new FileProcessor<>() {
      @Override
      public boolean apply(@NotNull T target, @NotNull File file, @NotNull R root) {
        hasDirtyFiles.set(true);
        return false;
      }
    });
    return hasDirtyFiles.get();
  }

  @Override
  public boolean hasRemovedFiles() {
    Map<BuildTarget<?>, Collection<Path>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    return map != null && !map.isEmpty();
  }

  @Override
  public @NotNull Collection<Path> getRemoved(@NotNull T target) {
    Map<BuildTarget<?>, Collection<Path>> map = Utils.REMOVED_SOURCES_KEY.get(myContext);
    if (map != null) {
      Collection<Path> paths = map.get(target);
      if (paths != null) {
        return paths;
      }
    }
    return List.of();
  }

  @SuppressWarnings("SSBasedInspection")
  @Override
  public @NotNull Collection<String> getRemovedFiles(@NotNull T target) {
    return getRemoved(target).stream().map(Path::toString).collect(Collectors.toList());
  }
}
