// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.definition;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface ToolWindowViewModelDescription {
  String getTitle();

  String getId();

  @NotNull
  Icon getIcon();

  ToolWindowPosition getPosition();
}