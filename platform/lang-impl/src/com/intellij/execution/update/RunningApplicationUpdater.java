// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.update;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface RunningApplicationUpdater {
  String getDescription();

  String getShortName();

  @Nullable
  Icon getIcon();

  default boolean isEnabled() {
    return true;
  }

  void performUpdate();
}
