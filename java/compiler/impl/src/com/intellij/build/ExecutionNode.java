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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ExecutionNode extends CachingSimpleNode {
  private final List<ExecutionNode> myChildrenList = ContainerUtil.newSmartList();
  private String myText;
  private long startTime;
  private long endTime;
  private boolean isFailed;
  private boolean isSkipped;

  public ExecutionNode(Project aProject) {
    super(aProject, null);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    return myChildrenList.size() == 0 ? NO_CHILDREN : ContainerUtil.toArray(myChildrenList, new ExecutionNode[myChildrenList.size()]);
  }

  @Override
  protected void doUpdate() {
    //setNameAndTooltip(getName(), null, myInfo.isUpToDate() ? "UP-TO-DATE" : null);
    setIcon(
      isRunning() ? ExecutionNodeProgressAnimator.getCurrentFrame() :
      isRunning() ? AllIcons.Process.State.GreyProgr :
      isFailed() ? AllIcons.Process.State.RedExcl :
      isSkipped() ? AllIcons.Process.State.YellowStr :
      AllIcons.Process.State.GreenOK
    );
  }

  @Override
  public String getName() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }

  public void add(ExecutionNode node) {
    myChildrenList.add(node);
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
    return isFailed;
  }

  public void setFailed(boolean failed) {
    isFailed = failed;
  }

  public boolean isSkipped() {
    return isSkipped;
  }

  public void setSkipped(boolean skipped) {
    isSkipped = skipped;
  }

  public boolean isRunning() {
    return endTime <= 0 && !isSkipped && !isFailed;
  }
}
