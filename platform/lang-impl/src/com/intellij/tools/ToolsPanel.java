// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

public final class ToolsPanel extends BaseToolsPanel<Tool> {
  @Override
  protected BaseToolManager<Tool> getToolManager() {
    return ToolManager.getInstance();
  }
}
