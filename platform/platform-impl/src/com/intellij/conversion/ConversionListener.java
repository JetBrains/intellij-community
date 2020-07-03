// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public interface ConversionListener {
  void conversionNeeded();

  default void successfullyConverted(@NotNull Path backupDir) {
  }

  void error(@NotNull String message);

  default void cannotWriteToFiles(@NotNull List<Path> readonlyFiles) {
  }
}
