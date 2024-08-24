// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.NioFiles;
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

  private final StampsStorage<? extends StampsStorage.Stamp> stampStorage;

  public ProjectStamps(Path dataStorageRoot, BuildTargetsState targetsState, PathRelativizerService relativizer) throws IOException {
    if (PORTABLE_CACHES) {
      stampStorage = new HashStampStorage(dataStorageRoot, relativizer, targetsState);
    }
    else {
      stampStorage = new FileTimestampStorage(dataStorageRoot, targetsState);
    }
  }

  /**
   * @deprecated Please use {@link #ProjectStamps(Path, BuildTargetsState, PathRelativizerService)}
   */
  @Deprecated
  public ProjectStamps(File dataStorageRoot, BuildTargetsState targetsState, PathRelativizerService relativizer) throws IOException {
    this(dataStorageRoot.toPath(), targetsState, relativizer);
  }

  public StampsStorage<? extends StampsStorage.Stamp> getStampStorage() {
    return stampStorage;
  }

  public void clean() {
    stampStorage.wipe();
  }

  public void close() {
    try {
      stampStorage.close();
    }
    catch (IOException e) {
      LOG.error(e);
      try {
        NioFiles.deleteRecursively(stampStorage.getStorageRoot());
      }
      catch (IOException ignore) {
      }
    }
  }
}
