/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.BeforeRunTask;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class ToolBeforeRunTask extends BeforeRunTask<ToolBeforeRunTask> {
  @NonNls private final static String ACTION_ID_ATTRIBUTE = "actionId";
  private String myToolActionId;

  protected ToolBeforeRunTask() {
    super(ToolBeforeRunTaskProvider.ID);
  }

  @Nullable
  public String getToolActionId() {
    return myToolActionId;
  }

  public void setToolActionId(String toolActionId) {
    myToolActionId = toolActionId;
  }

  public boolean isExecutable() {
    return myToolActionId != null;
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);
    if (myToolActionId != null) {
      element.setAttribute(ACTION_ID_ATTRIBUTE, myToolActionId);
    }
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);
    myToolActionId = element.getAttributeValue(ACTION_ID_ATTRIBUTE);
  }

  @Override
  public ToolBeforeRunTask clone() {
    return (ToolBeforeRunTask)super.clone();
  }

  public void execute(final DataContext context) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {

      @Override
      public void run() {
        ToolAction.runTool(myToolActionId, context);
      }
    });
  }

  @Nullable
  public Tool findCorrespondingTool() {
    if (myToolActionId == null) {
      return null;
    }
    Tool[] tools = ToolManager.getInstance().getTools();
    for (Tool tool : tools) {
      if (myToolActionId.equals(tool.getActionId())) {
        return tool;
      }
    }
    return null;
  }
}
