// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Eugene Zhuravlev
 */
public final class ProjectStamps {
  public static final String PORTABLE_CACHES_PROPERTY = "org.jetbrains.jps.portable.caches";
  public static final boolean PORTABLE_CACHES = Boolean.getBoolean(PORTABLE_CACHES_PROPERTY);

  public static final String TRACK_LIBRARY_CONTENT_PROPERTY = "org.jetbrains.jps.track.library.content";
  public static final boolean TRACK_LIBRARY_CONTENT = Boolean.getBoolean(TRACK_LIBRARY_CONTENT_PROPERTY);

  private static final Logger LOG = Logger.getInstance(ProjectStamps.class);

  private final StampsStorage<?> stampStorage;

  public ProjectStamps(@NotNull Path dataStorageRoot, @NotNull BuildTargetsState targetsState) throws IOException {
    this.stampStorage = new FileTimestampStorage(dataStorageRoot, targetsState);
  }

  /**
   * @deprecated Please use {@link #ProjectStamps(Path, BuildTargetsState)}
   */
  @SuppressWarnings("unused")
  @Deprecated
  public ProjectStamps(File dataStorageRoot, BuildTargetsState targetsState, PathRelativizerService relativizer) throws IOException {
    this(dataStorageRoot.toPath(), targetsState);
  }

  public @NotNull StampsStorage<?> getStampStorage() {
    return stampStorage;
  }

  public void close() {
    try {
      if (stampStorage instanceof StorageOwner) {
        ((StorageOwner)stampStorage).close();
      }
    }
    catch (IOException e) {
      LOG.error(e);
      try {
        Path root = stampStorage.getStorageRoot();
        if (root != null) {
          NioFiles.deleteRecursively(root);
        }
      }
      catch (IOException ignore) {
      }
    }
  }
}
