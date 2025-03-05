// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.jps.incremental.artifacts.instructions;

/**
 * Specifies target place in an artifact output which is located in the local file system (i.e. not inside a JAR file)
 */
public final class ExplodedDestinationInfo extends DestinationInfo {
  public ExplodedDestinationInfo(final String outputPath) {
    super(outputPath, outputPath);
  }

  @Override
  public String toString() {
    return getOutputPath();
  }
}
