// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.ide.ui.customization.CustomizableActionGroupProvider;

final class RunTabCustomizableActionGroupProvider extends CustomizableActionGroupProvider {
  @Override
  public void registerGroups(CustomizableActionGroupRegistrar registrar) {
    if (UIExperiment.isNewDebuggerUIEnabled()) {
      registrar.addCustomizableActionGroup(RunContentBuilder.RUN_TOOL_WINDOW_TOP_TOOLBAR_GROUP,
                                           ExecutionBundle.message("run.tool.window.header.toolbar"));
      registrar.addCustomizableActionGroup(RunContentBuilder.RUN_TOOL_WINDOW_TOP_TOOLBAR_MORE_GROUP,
                                           ExecutionBundle.message("run.tool.window.header.toolbar.more"));
    }
    else {
      registrar.addCustomizableActionGroup(RunContentBuilder.RUN_TOOL_WINDOW_TOP_TOOLBAR_OLD_GROUP,
                                           ExecutionBundle.message("run.tool.window.header.toolbar"));
    }
  }
}
