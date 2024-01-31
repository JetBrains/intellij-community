// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class CannotLoadJpsModelException extends RuntimeException {
  @NotNull private final File myFile;

  public CannotLoadJpsModelException(@NotNull File file, @NotNull String message, @Nullable Throwable cause) {
    super(message, cause);
    myFile = file;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }
}
