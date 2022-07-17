// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public abstract class NextChangeAction extends AnAction implements DumbAware {
  @NotNull public static final String ID = "Diff.NextChange";

  public NextChangeAction() {
    ActionUtil.copyFrom(this, ID);
  }
}
