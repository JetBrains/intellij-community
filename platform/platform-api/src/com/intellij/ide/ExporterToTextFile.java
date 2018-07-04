/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.TooManyListenersException;

public interface ExporterToTextFile {
  @Nullable
  default JComponent getSettingsEditor() {
    return null;
  }

  default void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
  }

  default void removeSettingsChangedListener(ChangeListener listener) {
  }

  @NotNull
  String getReportText();

  @NotNull
  String getDefaultFilePath();

  default void exportedTo(@NotNull String filePath) {
  }

  boolean canExport();
}
