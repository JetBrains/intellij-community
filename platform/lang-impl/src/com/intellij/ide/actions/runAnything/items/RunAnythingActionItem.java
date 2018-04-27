// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.runAnything.RunAnythingAction;
import com.intellij.ide.actions.runAnything.RunAnythingUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class RunAnythingActionItem extends RunAnythingItem<AnAction> {
  @NotNull private final AnAction myAction;
  @NotNull private final String myText;

  public RunAnythingActionItem(@NotNull AnAction action, @NotNull String text) {
    myAction = action;
    myText = text;
  }

  @Override
  public void run(@NotNull DataContext dataContext) {
    Component focusOwner = dataContext.getData(RunAnythingAction.FOCUS_COMPONENT_KEY_NAME);
    AnActionEvent event = dataContext.getData(RunAnythingAction.RUN_ANYTHING_EVENT_KEY);
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    RunAnythingUtil.performRunAnythingAction(myAction, project, focusOwner, event);
  }

  @NotNull
  @Override
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public String getAdText() {
    return RunAnythingUtil.AD_ACTION_TEXT;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return ObjectUtils.notNull(myAction.getTemplatePresentation().getIcon(), AllIcons.Toolwindows.ToolWindowRun);
  }

  @NotNull
  @Override
  public AnAction getValue() {
    return myAction;
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    return RunAnythingUtil.createActionCellRendererComponent(myAction, isSelected, myText);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingActionItem item = (RunAnythingActionItem)o;
    return Objects.equals(myAction, item.myAction) &&
           Objects.equals(myText, item.myText);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myAction, myText);
  }
}