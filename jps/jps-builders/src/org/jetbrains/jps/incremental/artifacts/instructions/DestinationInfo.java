/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;

/**
 * Specifies target place in an artifact output where source file will be copied to.
 *
 * @see JarDestinationInfo
 * @see ExplodedDestinationInfo
 * @author nik
 */
public abstract class DestinationInfo {
  private final String myOutputPath;
  private final String myOutputFilePath;

  protected DestinationInfo(@NotNull final String outputPath, @NotNull String outputFilePath) {
    myOutputPath = outputPath;
    myOutputFilePath = outputFilePath;
  }

  /**
   * @return full path to the target file including path inside JAR if it's located in a JAR file
   */
  @NotNull
  public String getOutputPath() {
    return myOutputPath;
  }

  /**
   * @return path to the target file if it isn't inside a JAR or path to the top-level JAR file containing it
   */
  @NotNull
  public String getOutputFilePath() {
    return myOutputFilePath;
  }
}
