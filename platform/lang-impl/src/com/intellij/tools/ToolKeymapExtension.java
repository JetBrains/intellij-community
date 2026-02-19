// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.openapi.keymap.KeyMapBundle;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public final class ToolKeymapExtension extends BaseToolKeymapExtension {
  private final ToolManager myToolManager;

  public ToolKeymapExtension() {
    myToolManager = ToolManager.getInstance();
  }

  @Override
  protected String getGroupIdPrefix() {
    return myToolManager.getGroupIdPrefix();
  }

  @Override
  protected String getActionIdPrefix() {
    return Tool.ACTION_ID_PREFIX;
  }

  @Override
  protected List<? extends Tool> getToolsIdsByGroupName(String groupName) {
    return myToolManager.getTools(groupName);
  }

  @Override
  protected String getRootGroupName() {
    return KeyMapBundle.message("actions.tree.external.tools.group");
  }

  @Override
  protected String getRootGroupId() {
    return "ExternalToolsGroup";
  }
}
