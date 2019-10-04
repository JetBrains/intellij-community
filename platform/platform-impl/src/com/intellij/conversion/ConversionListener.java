// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public interface ConversionListener {
  void conversionNeeded();

  void successfullyConverted(@NotNull File backupDir);

  void error(@NotNull String message);

  void cannotWriteToFiles(@NotNull List<? extends File> readonlyFiles);
}
