// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.viewModel.extraction;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.viewModel.definition.ToolWindowViewModelContent;
import com.intellij.ui.viewModel.definition.ToolWindowViewModelDescription;

public interface ToolWindowViewModelExtractor {
  ExtensionPointName<ToolWindowViewModelExtractor> EP_NAME = ExtensionPointName.create("com.intellij.toolWindowExtractor");

  ToolWindowViewModelContent extractViewModel(ToolWindow window, Project project);

  ToolWindowViewModelDescription extractDescription(ToolWindow window);

  boolean isApplicable(String toolWindowId);
}