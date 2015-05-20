/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.view;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2014
 */
public class TaskNode extends ExternalSystemNode<TaskData> {
  private TaskData myTaskData;

  public TaskNode(@NotNull ExternalProjectsView externalProjectsView, @NotNull DataNode<TaskData> dataNode) {
    super(externalProjectsView, null, dataNode);
    myTaskData = dataNode.getData();
  }

  @Override
  protected void update(PresentationData presentation) {
    super.update(presentation);
    presentation.setIcon(getUiAware().getTaskIcon());

    final String shortcutHint = StringUtil.nullize(getShortcutsManager().getDescription(myTaskData.getLinkedExternalProjectPath(), myTaskData.getName()));
    final String activatorHint = StringUtil.nullize(getTaskActivator().getDescription(
      myTaskData.getOwner(), myTaskData.getLinkedExternalProjectPath(), myTaskData.getName()));

    String hint;
    if (shortcutHint == null) {
      hint = activatorHint;
    }
    else if (activatorHint == null) {
      hint = shortcutHint;
    }
    else {
      hint = shortcutHint + ", " + activatorHint;
    }

    setNameAndTooltip(getName(), myTaskData.getDescription(), hint);
  }

  @Override
  public boolean isVisible() {
    if (!super.isVisible()) return false;
    return !myTaskData.isInherited() || getExternalProjectsView().showInheritedTasks();
  }

  @Override
  public String getName() {
    return myTaskData.getName();
  }

  @Nullable
  @Override
  protected String getMenuId() {
    return "ExternalSystemView.TaskMenu";
  }

  @Nullable
  @Override
  protected String getActionId() {
    return "ExternalSystem.RunTask";
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }
}
