// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.DiffContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class ComponentDiffRequest extends DiffRequest {
  public abstract @NotNull JComponent getComponent(@NotNull DiffContext context);
}
