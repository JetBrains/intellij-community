// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard.importSources;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class DetectedProjectRoot {
  private final File myDirectory;

  protected DetectedProjectRoot(@NotNull File directory) {
    myDirectory = directory;
  }

  public File getDirectory() {
    return myDirectory;
  }

  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getRootTypeName();

  public @Nullable DetectedProjectRoot combineWith(@NotNull DetectedProjectRoot root) {
    return null;
  }

  public boolean canContainRoot(@NotNull DetectedProjectRoot root) {
    return true;
  }
}
