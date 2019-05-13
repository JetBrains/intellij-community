/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.model.java;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author nik
 */
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
    if (!Comparing.equal(myRelativeOutputPath, relativeOutputPath)) {
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

  @Override
  public void applyChanges(@NotNull JavaResourceRootProperties modified) {
    setRelativeOutputPath(modified.myRelativeOutputPath);
    setForGeneratedSources(modified.myForGeneratedSources);
  }
}
