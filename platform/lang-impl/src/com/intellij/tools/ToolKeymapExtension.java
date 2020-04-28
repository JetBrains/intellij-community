/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.tools;

import com.intellij.openapi.keymap.KeyMapBundle;

import java.util.List;

public class ToolKeymapExtension extends BaseToolKeymapExtension {

  private final ToolManager myToolManager;

  public ToolKeymapExtension() {
    myToolManager = ToolManager.getInstance();
  }

  @Override
  protected String getGroupIdPrefix() {
    return ExternalToolsGroup.GROUP_ID_PREFIX;
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
