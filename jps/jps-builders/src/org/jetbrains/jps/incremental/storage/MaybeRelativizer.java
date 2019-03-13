// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;

import java.io.File;

public class MaybeRelativizer { // see PathMacroMap and friends around
  @Nullable private final String myProjectPath;
  private static final String PRJ = "$PROJECT_DIR$";

  public MaybeRelativizer(JpsProject project) {
    File projectBaseDirectory = JpsModelSerializationDataService.getBaseDirectory(project);
    myProjectPath = projectBaseDirectory != null ? projectBaseDirectory.getAbsolutePath() : null;
  }

  @TestOnly
  public MaybeRelativizer() {
    myProjectPath = null;
  }

  @NotNull
  public String toRelative(@NotNull String path) {
    if (myProjectPath == null) return path;
    int i = path.indexOf(myProjectPath);
    if (i < 0) return path;
    return PRJ + path.substring(i + myProjectPath.length());
  }

  @NotNull
  public String toFull(@NotNull String path) {
    if (myProjectPath == null) return path;
    int i = path.indexOf(PRJ);
    if (i < 0) return path;
    return myProjectPath + path.substring(i + PRJ.length());
  }
}
