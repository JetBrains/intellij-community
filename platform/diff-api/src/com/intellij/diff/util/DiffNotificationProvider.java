// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.util;

import com.intellij.diff.FrameDiffTool;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface DiffNotificationProvider {
  @Nullable
  JComponent createNotification(@Nullable FrameDiffTool.DiffViewer viewer);
}
