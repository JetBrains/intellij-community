// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.view;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.Named;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;
import static com.intellij.openapi.externalSystem.model.ProjectKeys.PROJECT;

/**
 * @author Vladislav.Soroka
 */
public class TaskNode extends ExternalSystemNode<TaskData> {
  private final TaskData myTaskData;
  private String moduleOwnerName;

  public TaskNode(@NotNull ExternalProjectsView externalProjectsView, @NotNull DataNode<TaskData> dataNode) {
    super(externalProjectsView, null, dataNode);
    myTaskData = dataNode.getData();

    DataNode parent = ExternalSystemApiUtil.findParent(dataNode, MODULE);
    if (parent == null) {
      parent = ExternalSystemApiUtil.findParent(dataNode, PROJECT);
    }
    if(parent != null && parent.getData() instanceof Named) {
      moduleOwnerName = ((Named)parent.getData()).getInternalName();
    }
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
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

    setNameAndTooltip(presentation, getName(), myTaskData.getDescription(), hint);
  }

  @Override
  public boolean isVisible() {
    if (!super.isVisible()) return false;
    return !myTaskData.isInherited() || getExternalProjectsView().showInheritedTasks();
  }

  public boolean isTest() {
    return myTaskData.isTest();
  }

  public String getModuleOwnerName() {
    return moduleOwnerName;
  }

  @Override
  protected @Nullable String getMenuId() {
    return "ExternalSystemView.TaskMenu";
  }

  @Override
  protected @Nullable String getActionId() {
    return "ExternalSystem.RunTask";
  }

  @Override
  public boolean isAlwaysLeaf() {
    return true;
  }
}
