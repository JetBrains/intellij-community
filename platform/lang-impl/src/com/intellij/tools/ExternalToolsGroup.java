// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExternalToolsGroup extends BaseExternalToolsGroup<Tool> {
  public static final String GROUP_ID_PREFIX = "ExternalTools_";

  @Override
  protected List<ToolsGroup<Tool>> getToolsGroups() {
    return ToolManager.getInstance().getGroups();
  }

  @Override
  protected @NonNls @NotNull String getGroupIdPrefix() {
    return GROUP_ID_PREFIX;
  }

  @Override
  protected List<Tool> getToolsByGroupName(String groupName) {
    return ToolManager.getInstance().getTools(groupName);
  }

  @Override
  protected ToolAction createToolAction(Tool tool) {
    return new ToolAction(tool);
  }
}
