// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.Objects;

public final class JavaResourceRootProperties extends JpsElementBase<JavaResourceRootProperties> {
  private String myRelativeOutputPath;
  private boolean myForGeneratedSources;

  public JavaResourceRootProperties(@NotNull String relativeOutputPath, boolean forGeneratedSources) {
    myRelativeOutputPath = relativeOutputPath;
    myForGeneratedSources = forGeneratedSources;
  }

  /**
   * @return relative path to the target directory under the module output directory for resource files from this root
   */
  public @NotNull String getRelativeOutputPath() {
    return myRelativeOutputPath;
  }

  @Override
  public @NotNull JavaResourceRootProperties createCopy() {
    return new JavaResourceRootProperties(myRelativeOutputPath, myForGeneratedSources);
  }

  public boolean isForGeneratedSources() {
    return myForGeneratedSources;
  }

  @ApiStatus.Internal
  public void setRelativeOutputPath(@NotNull String relativeOutputPath) {
    if (!Objects.equals(myRelativeOutputPath, relativeOutputPath)) {
      myRelativeOutputPath = relativeOutputPath;
    }
  }

  @ApiStatus.Internal
  public void setForGeneratedSources(boolean forGeneratedSources) {
    if (myForGeneratedSources != forGeneratedSources) {
      myForGeneratedSources = forGeneratedSources;
    }
  }
}
