// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

public final class GroupByLeavesAction extends AnAction {
  private final SliceTreeBuilder myTreeBuilder;

  public GroupByLeavesAction(@NotNull SliceTreeBuilder treeBuilder) {
    super(LangBundle.messagePointer("action.GroupByLeavesAction.show.original.expression.values.text"),
          LangBundle.messagePointer("action.GroupByLeavesAction.show.original.expression.values.description"), PlatformIcons.XML_TAG_ICON);
    myTreeBuilder = treeBuilder;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(LangBundle.message("action.GroupByLeavesAction.show.original.expression.values.text") +
                                (myTreeBuilder.analysisInProgress
                                 ? " " + LangBundle.message("action.GroupByLeavesAction.analysis.in.progress.text") : ""));
    e.getPresentation().setEnabled(isAvailable());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private boolean isAvailable() {
    return !myTreeBuilder.analysisInProgress && !myTreeBuilder.splitByLeafExpressions;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myTreeBuilder.switchToGroupedByLeavesNodes();
  }
}
