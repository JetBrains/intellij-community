// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class ProjectStamps implements StorageOwner {
  public static final String PORTABLE_CACHES_PROPERTY = "org.jetbrains.jps.portable.caches";
  public static final boolean PORTABLE_CACHES = Boolean.getBoolean(PORTABLE_CACHES_PROPERTY);

  public static final String TRACK_LIBRARY_CONTENT_PROPERTY = "org.jetbrains.jps.track.library.content";
  public static final boolean TRACK_LIBRARY_CONTENT = Boolean.getBoolean(TRACK_LIBRARY_CONTENT_PROPERTY);

  private final FileTimestampStorage stampStorage;

  @ApiStatus.Internal
  public ProjectStamps(@NotNull Path dataStorageRoot, @NotNull BuildTargetStateManager targetStateManager) throws IOException {
    stampStorage = new FileTimestampStorage(dataStorageRoot, targetStateManager);
  }

  /**
   * @deprecated Please use {@link #ProjectStamps(Path, BuildTargetStateManager)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  @ApiStatus.Internal
  public ProjectStamps(@NotNull File dataStorageRoot, BuildTargetsState targetsState, PathRelativizerService relativizer) throws IOException {
    this(dataStorageRoot.toPath(), targetsState.impl);
  }

  public @NotNull StampsStorage<?> getStampStorage() {
    return stampStorage;
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    stampStorage.flush(memoryCachesOnly);
  }

  @Override
  public void clean() throws IOException {
    stampStorage.clean();
  }

  @Override
  public void close() throws IOException {
    stampStorage.close();
  }
}
