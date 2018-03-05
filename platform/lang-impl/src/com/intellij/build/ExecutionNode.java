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
import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class ExecutionNode extends CachingSimpleNode {
  private final List<ExecutionNode> myChildrenList = ContainerUtil.newSmartList();
  private long startTime;
  private long endTime;
  @Nullable
  private String myTitle;
  @Nullable
  private String myTooltip;
  @Nullable
  private String myHint;
  @Nullable
  private EventResult myResult;
  private boolean myAutoExpandNode;
  @Nullable
  private Navigatable myNavigatable;
  @Nullable
  private NullableLazyValue<Icon> myPreferredIconValue;
  private final AtomicInteger myErrors = new AtomicInteger();
  private final AtomicInteger myWarnings = new AtomicInteger();

  public ExecutionNode(Project aProject, ExecutionNode parentNode) {
    super(aProject, parentNode);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    return myChildrenList.size() == 0 ? NO_CHILDREN : ContainerUtil.toArray(myChildrenList, new ExecutionNode[myChildrenList.size()]);
  }

  @Override
  protected void update(PresentationData presentation) {
    setIcon(getCurrentIcon());
    presentation.setPresentableText(myName);
    presentation.setIcon(getIcon());
    if (myTitle != null) {
      presentation.addText(myTitle + ": ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    String hint = getCurrentHint();
    if (myTitle != null || hint != null) {
      presentation.addText(myName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    if (hint != null) {
      presentation.addText("  " + hint, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
    if (myTooltip != null) {
      presentation.setTooltip(myTooltip);
    }
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
    return myTitle;
  }

  public void setTitle(@Nullable String title) {
    myTitle = title;
  }

  @Nullable
  public String getTooltip() {
    return myTooltip;
  }

  public void setTooltip(@Nullable String tooltip) {
    myTooltip = tooltip;
  }

  @Nullable
  public String getHint() {
    return myHint;
  }

  public void setHint(@Nullable String hint) {
    myHint = hint;
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
    if (startTime == endTime) return null;
    if (isRunning()) {
      final long duration = startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
      String durationText = StringUtil.formatDuration(duration);
      int index = durationText.indexOf("s ");
      if (index != -1) {
        durationText = durationText.substring(0, index + 1);
      }
      return "Running for " + durationText;
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

  public void setNavigatable(@Nullable Navigatable navigatable) {
    myNavigatable = navigatable;
  }

  @NotNull
  public List<Navigatable> getNavigatables() {
    if (myNavigatable != null) {
      return Collections.singletonList(myNavigatable);
    }
    if (myResult == null) return Collections.emptyList();

    if (myResult instanceof FailureResult) {
      List<Navigatable> result = new SmartList<>();
      for (Failure failure : ((FailureResult)myResult).getFailures()) {
        ContainerUtil.addIfNotNull(result, failure.getNavigatable());
      }
      return result;
    }
    return Collections.emptyList();
  }

  public void setIconProvider(Supplier<Icon> iconProvider) {
    myPreferredIconValue = new NullableLazyValue<Icon>() {
      @Nullable
      @Override
      protected Icon compute() {
        return iconProvider.get();
      }
    };
  }

  public void reportChildMessageKind(MessageEvent.Kind kind) {
    if (kind == MessageEvent.Kind.ERROR) {
      myErrors.incrementAndGet();
    }
    else if (kind == MessageEvent.Kind.WARNING) {
      myWarnings.incrementAndGet();
    }
  }

  private String getCurrentHint() {
    String hint = myHint;
    int warnings = myWarnings.get();
    int errors = myErrors.get();
    if (warnings > 0 || errors > 0) {
      if (hint == null) {
        hint = "";
      }
      hint += (getParent() == null ? isRunning() ? "   " : "   with " : " (");
      if (errors > 0) {
        hint += (errors + " " + StringUtil.pluralize("error", errors));
        if (warnings > 0) {
          hint += ", ";
        }
      }
      if (warnings > 0) {
        hint += (warnings + " " + StringUtil.pluralize("warning", warnings));
      }
      if (getParent() != null) {
        hint += ")";
      }
    }
    return hint;
  }

  private Icon getCurrentIcon() {
    if (myPreferredIconValue != null) {
      return myPreferredIconValue.getValue();
    }
    else if (myResult instanceof MessageEventResult) {
      return getIcon(((MessageEventResult)myResult).getKind());
    }
    else {
      return isRunning() ? ExecutionNodeProgressAnimator.getCurrentFrame() :
             isFailed() ? AllIcons.Process.State.RedExcl :
             isSkipped() ? AllIcons.Process.State.YellowStr :
             AllIcons.Process.State.GreenOK;
    }
  }

  private static Icon getIcon(MessageEvent.Kind kind) {
    switch (kind) {
      case ERROR:
        return AllIcons.General.Error;
      case WARNING:
        return AllIcons.General.Warning;
      case INFO:
        return AllIcons.General.Information;
      case STATISTICS:
        return AllIcons.General.Mdot_empty;
      case SIMPLE:
        return AllIcons.General.Mdot_empty;
    }
    return AllIcons.General.Mdot_empty;
  }
}
