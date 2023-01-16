// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildOutputConsumer;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class OutputTracker implements BuildOutputConsumer {
  private final BuildOutputConsumer myDelegate;
  private int myFilesRegistered = 0;
  private int myDirsRegistered = 0;

  public OutputTracker(BuildOutputConsumer delegate) {
    myDelegate = delegate;
  }

  @Override
  public void registerOutputFile(@NotNull File outputFile, @NotNull Collection<String> sourcePaths) throws IOException {
    myFilesRegistered++;
    myDelegate.registerOutputFile(outputFile, sourcePaths);
  }

  @Override
  public void registerOutputDirectory(@NotNull File outputDir, @NotNull Collection<String> sourcePaths) throws IOException {
    myDirsRegistered++;
    myDelegate.registerOutputDirectory(outputDir, sourcePaths);
  }

  public int getRegisteredFilesCount() {
    return myFilesRegistered;
  }

  public int getRegisteredDirsCount() {
    return myDirsRegistered;
  }

  public boolean isOutputGenerated() {
    return myFilesRegistered > 0 || myDirsRegistered > 0;
  }

  public static OutputTracker create(BuildOutputConsumer consumer) {
    return new OutputTracker(consumer);
  }
}
