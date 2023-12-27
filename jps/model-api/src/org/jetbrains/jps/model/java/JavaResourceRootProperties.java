// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.Objects;

public class JavaResourceRootProperties extends JpsElementBase<JavaResourceRootProperties> {
  private String myRelativeOutputPath = "";
  private boolean myForGeneratedSources;

  public JavaResourceRootProperties(@NotNull String relativeOutputPath, boolean forGeneratedSources) {
    myRelativeOutputPath = relativeOutputPath;
    myForGeneratedSources = forGeneratedSources;
  }

  /**
   * @return relative path to the target directory under the module output directory for resource files from this root
   */
  @NotNull
  public String getRelativeOutputPath() {
    return myRelativeOutputPath;
  }

  @NotNull
  @Override
  public JavaResourceRootProperties createCopy() {
    return new JavaResourceRootProperties(myRelativeOutputPath, myForGeneratedSources);
  }

  public boolean isForGeneratedSources() {
    return myForGeneratedSources;
  }

  public void setRelativeOutputPath(@NotNull String relativeOutputPath) {
    if (!Objects.equals(myRelativeOutputPath, relativeOutputPath)) {
      myRelativeOutputPath = relativeOutputPath;
      fireElementChanged();
    }
  }

  public void setForGeneratedSources(boolean forGeneratedSources) {
    if (myForGeneratedSources != forGeneratedSources) {
      myForGeneratedSources = forGeneratedSources;
      fireElementChanged();
    }
  }
}
