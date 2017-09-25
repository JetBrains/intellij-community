/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build;

import com.intellij.build.events.*;
import com.intellij.build.events.impl.FailureImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ExecutionNode extends CachingSimpleNode {
  private final List<ExecutionNode> myChildrenList = ContainerUtil.newSmartList();
  private long startTime;
  private long endTime;
  @Nullable
  private String title;
  @Nullable
  private String tooltip;
  @Nullable
  private String hint;
  @Nullable
  private EventResult myResult;
  private boolean myAutoExpandNode;

  public ExecutionNode(Project aProject) {
    super(aProject, null);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    return myChildrenList.size() == 0 ? NO_CHILDREN : ContainerUtil.toArray(myChildrenList, new ExecutionNode[myChildrenList.size()]);
  }

  @Override
  protected void update(PresentationData presentation) {
    super.update(presentation);
    if (title != null) {
      presentation.addText(title + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    if(title != null || hint != null) {
      presentation.addText(myName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    if (hint != null) {
      presentation.addText("  " + hint, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    if (tooltip != null) {
      presentation.setTooltip(tooltip);
    }
  }

  @Override
  protected void doUpdate() {
    setIcon(
      isRunning() ? ExecutionNodeProgressAnimator.getCurrentFrame() :
      isFailed() ? AllIcons.Process.State.RedExcl :
      isSkipped() ? AllIcons.Process.State.YellowStr :
      AllIcons.Process.State.GreenOK
    );
  }

  @Override
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @Nullable
  public String getTitle() {
    return title;
  }

  public void setTitle(@Nullable String title) {
    this.title = title;
  }

  @Nullable
  public String getTooltip() {
    return tooltip;
  }

  public void setTooltip(@Nullable String tooltip) {
    this.tooltip = tooltip;
  }

  @Nullable
  public String getHint() {
    return hint;
  }

  public void setHint(@Nullable String hint) {
    this.hint = hint;
  }

  public void add(ExecutionNode node) {
    myChildrenList.add(node);
    cleanUpCache();
  }

  public void add(int index, ExecutionNode node) {
    myChildrenList.add(index, node);
    cleanUpCache();
  }

  void removeChildren() {
    myChildrenList.clear();
    cleanUpCache();
  }

  @Nullable
  public String getDuration() {
    if (isRunning()) {
      final long duration = startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
      return "Running for " + StringUtil.formatDuration(duration);
    }
    else {
      return isSkipped() ? null : StringUtil.formatDuration(endTime - startTime);
    }
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public boolean isFailed() {
    return myResult instanceof FailureResult;
  }

  public boolean isSkipped() {
    return myResult instanceof SkippedResult;
  }

  public boolean isRunning() {
    return endTime <= 0 && !isSkipped() && !isFailed();
  }

  public void setResult(@Nullable EventResult result) {
    myResult = result;
  }

  @Nullable
  public EventResult getResult() {
    return myResult;
  }

  @Override
  public boolean isAutoExpandNode() {
    return myAutoExpandNode;
  }

  public void setAutoExpandNode(boolean autoExpandNode) {
    myAutoExpandNode = autoExpandNode;
  }

  @NotNull
  public List<Navigatable> getNavigatables() {
    if (myResult == null) return Collections.emptyList();

    if (myResult instanceof FailureResult) {
      List<Navigatable> result = new SmartList<>();
      for (Failure failure : ((FailureResult)myResult).getFailures()) {
        NotificationData notificationData = ((FailureImpl)failure).getNotificationData();
        if (notificationData != null) {
          ContainerUtil.addIfNotNull(result, notificationData.getNavigatable());
        }
      }
      return result;
    }
    return Collections.emptyList();
  }
}
