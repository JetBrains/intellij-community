// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;

/**
 * @deprecated use {@link ProjectStamps} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
public class ProjectTimestamps extends ProjectStamps {
  public ProjectTimestamps(File dataStorageRoot,
                           BuildTargetsState targetsState,
                           PathRelativizerService relativizer) throws IOException {
    super(dataStorageRoot, targetsState, relativizer);
  }
}
