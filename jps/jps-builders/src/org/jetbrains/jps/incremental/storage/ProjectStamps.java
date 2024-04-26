// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public final class ProjectStamps {
  public static final String PORTABLE_CACHES_PROPERTY = "org.jetbrains.jps.portable.caches";
  public static final boolean PORTABLE_CACHES = Boolean.getBoolean(PORTABLE_CACHES_PROPERTY);

  public static final String TRACK_LIBRARY_CONTENT_PROPERTY = "org.jetbrains.jps.track.library.content";
  public static final boolean TRACK_LIBRARY_CONTENT = Boolean.getBoolean(TRACK_LIBRARY_CONTENT_PROPERTY);


  private static final Logger LOG = Logger.getInstance(ProjectStamps.class);

  private final StampsStorage<? extends StampsStorage.Stamp> myStampsStorage;

  public ProjectStamps(File dataStorageRoot,
                       BuildTargetsState targetsState,
                       PathRelativizerService relativizer) throws IOException {
    myStampsStorage = PORTABLE_CACHES
                      ? new FileStampStorage(dataStorageRoot, relativizer, targetsState)
                      : new FileTimestampStorage(dataStorageRoot, targetsState);
  }

  public StampsStorage<? extends StampsStorage.Stamp> getStampStorage() {
    return myStampsStorage;
  }

  public void clean() {
    myStampsStorage.wipe();
  }

  public void close() {
    try {
      myStampsStorage.close();
    }
    catch (IOException e) {
      LOG.error(e);
      FileUtil.delete(myStampsStorage.getStorageRoot());
    }
  }
}
