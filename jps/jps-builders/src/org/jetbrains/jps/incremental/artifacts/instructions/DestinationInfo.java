// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts.instructions;

import org.jetbrains.annotations.NotNull;

/**
 * Specifies target place in an artifact output where source file will be copied to.
 *
 * @see JarDestinationInfo
 * @see ExplodedDestinationInfo
 */
public abstract class DestinationInfo {
  private final String myOutputPath;
  private final String myOutputFilePath;

  protected DestinationInfo(final @NotNull String outputPath, @NotNull String outputFilePath) {
    myOutputPath = outputPath;
    myOutputFilePath = outputFilePath;
  }

  /**
   * @return full path to the target file including path inside JAR if it's located in a JAR file
   */
  public @NotNull String getOutputPath() {
    return myOutputPath;
  }

  /**
   * @return path to the target file if it isn't inside a JAR or path to the top-level JAR file containing it
   */
  public @NotNull String getOutputFilePath() {
    return myOutputFilePath;
  }
}
